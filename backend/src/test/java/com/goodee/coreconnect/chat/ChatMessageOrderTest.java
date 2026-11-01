package com.goodee.coreconnect.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.goodee.coreconnect.chat.entity.Chat;
import com.goodee.coreconnect.chat.entity.ChatRoom;
import com.goodee.coreconnect.chat.repository.ChatRepository;
import com.goodee.coreconnect.chat.service.ChatRoomService;
import com.goodee.coreconnect.security.jwt.JwtProvider;
import com.goodee.coreconnect.support.AbstractIntegrationTest;
import com.goodee.coreconnect.user.entity.User;
import com.goodee.coreconnect.user.enums.JobGrade;
import com.goodee.coreconnect.user.enums.Role;
import com.goodee.coreconnect.user.repository.UserRepository;

/**
 * 한 사람이 연달아 보낸 메시지가 보낸 순서대로 저장되는지 확인하는 재현 테스트
 *
 * 메시지를 쉬지 않고 보낸 뒤, 목록 API와 같은 기준(보낸 시각, 번호)으로 정렬해 본문 순서를 비교한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "jwt.secret=integration-test-jwt-secret-key-must-be-at-least-256-bits-long",
    "cloud.aws.credentials.access-key=test",
    "cloud.aws.credentials.secret-key=test",
    "cloud.aws.region.static=ap-northeast-2",
    "cloud.aws.s3.bucket=test-bucket",
    "sendgrid.api.key=test",
    "sendgrid.from.email=test@coreconnect.test",
    "sendgrid.from.name=CoreConnect",
    "sendgrid.reply.to=test@coreconnect.test",
    "spring.mail.host=localhost",
    "spring.mail.username=test@coreconnect.test"
})
@TestPropertySource(properties = "security.mode=secure")
class ChatMessageOrderTest extends AbstractIntegrationTest {

  private static final int MESSAGES = 60;

  @LocalServerPort private int port;
  @Autowired private JwtProvider jwtProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private ChatRoomService chatRoomService;
  @Autowired private ChatRepository chatRepository;

  private final WebSocketStompClient stompClient = newClient();

  @AfterEach
  void tearDown() {
    stompClient.stop();
  }

  @Test
  @DisplayName("한 사람이 연달아 보낸 메시지는 보낸 순서대로 저장된다")
  void burstMessages_keepSendingOrder() throws Exception {
    User sender = newUser("sender");
    User other = newUser("other");
    ChatRoom room = chatRoomService.createChatRoom("방", List.of(sender.getId(), other.getId()), sender.getEmail());

    BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
    connect(other).subscribe("/topic/chat.room." + room.getId(), new StompFrameHandler() {
      @Override
      public Type getPayloadType(StompHeaders headers) {
        return Map.class;
      }

      @Override
      @SuppressWarnings("unchecked")
      public void handleFrame(StompHeaders headers, Object payload) {
        inbox.add((Map<String, Object>) payload);
      }
    });
    Thread.sleep(500);

    StompSession session = connect(sender);
    List<String> sent = new ArrayList<>();
    for (int i = 0; i < MESSAGES; i++) {
      String content = "m" + i;
      sent.add(content);
      StompHeaders headers = new StompHeaders();
      headers.setDestination("/app/chat.sendMessage");
      session.send(headers, Map.of("roomId", room.getId(), "content", content));
    }

    Awaitility.await().atMost(120, TimeUnit.SECONDS) // 한 연결의 메시지는 하나씩 처리되므로 전체 시험 중에는 오래 걸린다
        .until(() -> chatRepository.findByChatRoomId(room.getId()).size() == MESSAGES);

    List<String> saved = chatRepository.findByChatRoomId(room.getId()).stream()
        .sorted(Comparator.comparing(Chat::getSendAt).thenComparing(Chat::getId))
        .map(Chat::getMessageContent)
        .toList();
    assertThat(saved).containsExactlyElementsOf(sent);

    List<String> received = new ArrayList<>();
    while (received.size() < MESSAGES) {
      Map<String, Object> message = inbox.poll(5, TimeUnit.SECONDS);
      assertThat(message).isNotNull();
      if (message.get("messageContent") != null) { // 메시지 외의 방송(안 읽은 수 갱신 등)은 건너뜀
        received.add((String) message.get("messageContent"));
      }
    }
    assertThat(received).containsExactlyElementsOf(sent);
  }

  private static WebSocketStompClient newClient() {
    WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    client.setMessageConverter(new MappingJackson2MessageConverter());
    return client;
  }

  private StompSession connect(User user) throws Exception {
    WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
    handshake.add("Cookie", "access_token=" + jwtProvider.createAccess(user.getEmail(), user.getRole(), 30));
    return stompClient
        .connectAsync("ws://localhost:" + port + "/ws/chat-ws", handshake, new StompHeaders(),
            new StompSessionHandlerAdapter() {})
        .get(5, TimeUnit.SECONDS);
  }

  private User newUser(String prefix) {
    String email = prefix + "-" + UUID.randomUUID() + "@test.com";
    return userRepository.save(User.createUser("pw", prefix, Role.USER, email, null, null, JobGrade.STAFF, null));
  }
}
