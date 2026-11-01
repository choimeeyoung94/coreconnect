package com.goodee.coreconnect.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

import com.goodee.coreconnect.chat.entity.Chat;
import com.goodee.coreconnect.chat.entity.ChatMessageReadStatus;
import com.goodee.coreconnect.chat.entity.ChatRoom;
import com.goodee.coreconnect.chat.repository.ChatMessageReadStatusRepository;
import com.goodee.coreconnect.chat.repository.ChatRepository;
import com.goodee.coreconnect.chat.service.ChatRoomService;
import com.goodee.coreconnect.support.AbstractIntegrationTest;
import com.goodee.coreconnect.user.entity.User;
import com.goodee.coreconnect.user.enums.JobGrade;
import com.goodee.coreconnect.user.enums.Role;
import com.goodee.coreconnect.user.repository.UserRepository;

/**
 * 같은 사용자의 읽음 요청이 겹칠 때 DB 교착 상태가 나지 않는지 확인하는 재현 테스트
 *
 * 실제 교착 상태는 MySQL 실행 계획이 바뀌어 조회 순서가 달라질 때 났다. 실행 계획은 테스트에서 고정할 수 없어서,
 * 요청 절반에는 조회 결과를 뒤집어 돌려준다. 조회 순서가 달라도 잠금 순서는 같아야 한다.
 */
class ChatReadDeadlockTest extends AbstractIntegrationTest {

  private static final int MESSAGES = 200;
  private static final int REQUESTS = 12;
  private static final ThreadLocal<Boolean> REVERSED = ThreadLocal.withInitial(() -> false);

  @Autowired private UserRepository userRepository;
  @Autowired private ChatRoomService chatRoomService;
  @Autowired private ChatRepository chatRepository;
  @SpyBean private ChatMessageReadStatusRepository readStatusRepository;

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("같은 사용자의 읽음 요청이 동시에 와도 전부 성공하고 안 읽음이 남지 않는다")
  void concurrentReads_allSucceed() throws Exception {
    User sender = newUser("sender");
    User reader = newUser("reader");
    ChatRoom room = roomOf(sender, reader);
    newUnreadMessages(room, sender, reader, MESSAGES);

    Answer<?> realCall = mockingDetails(readStatusRepository).getMockCreationSettings().getDefaultAnswer();
    doAnswer((call) -> {
      List<Integer> found = new ArrayList<>((List<Integer>) realCall.answer(call));
      if (REVERSED.get()) {
        Collections.reverse(found);
      }
      return found;
    }).when(readStatusRepository).findUnreadChatIds(any(), any(), any());

    ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> requests = new ArrayList<>();
    for (int i = 0; i < REQUESTS; i++) {
      boolean reversed = i % 2 == 0;
      requests.add(pool.submit(() -> {
        REVERSED.set(reversed);
        start.await();
        return chatRoomService.markMessagesAsRead(room.getId(), reader.getId());
      }));
    }
    start.countDown();

    List<Throwable> failures = new ArrayList<>();
    for (Future<?> request : requests) {
      try {
        request.get();
      } catch (Exception e) {
        failures.add(e.getCause());
      }
    }
    pool.shutdown();

    assertThat(failures).isEmpty();
    assertThat(readStatusRepository.countByUserIdAndChatRoomIdAndReadYnFalse(reader.getId(), room.getId())).isZero();
  }

  @Test
  @DisplayName("받은 번호까지만 읽음 처리하고, 처리한 메시지 번호를 순서대로 돌려준다")
  void readsOnlyUpToChatId_inOrder() {
    User sender = newUser("sender");
    User reader = newUser("reader");
    ChatRoom room = roomOf(sender, reader);
    newUnreadMessages(room, sender, reader, 5);
    List<Integer> chatIds = readStatusRepository.findUnreadChatIds(room.getId(), reader.getId(), null);

    List<Integer> read = chatRoomService.markMessagesAsRead(room.getId(), reader.getId(), chatIds.get(2));

    assertThat(read).containsExactlyElementsOf(chatIds.subList(0, 3));
    assertThat(readStatusRepository.findUnreadChatIds(room.getId(), reader.getId(), null))
        .containsExactlyElementsOf(chatIds.subList(3, 5));
    assertThat(chatRoomService.markMessagesAsRead(room.getId(), reader.getId(), chatIds.get(2))).isEmpty();
  }

  private void newUnreadMessages(ChatRoom room, User sender, User reader, int count) {
    for (int i = 0; i < count; i++) {
      Chat chat = chatRepository.save(Chat.createChat(room, sender, "m" + i, false, null, LocalDateTime.now()));
      readStatusRepository.save(ChatMessageReadStatus.create(chat, reader));
    }
  }

  private User newUser(String prefix) {
    String email = prefix + "-" + UUID.randomUUID() + "@test.com";
    return userRepository.save(User.createUser("pw", prefix, Role.USER, email, null, null, JobGrade.STAFF, null));
  }

  private ChatRoom roomOf(User creator, User other) {
    return chatRoomService.createChatRoom("방", List.of(creator.getId(), other.getId()), creator.getEmail());
  }
}
