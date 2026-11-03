package com.goodee.coreconnect.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

import com.goodee.coreconnect.chat.entity.Chat;
import com.goodee.coreconnect.chat.entity.ChatRoom;
import com.goodee.coreconnect.chat.repository.NotificationRepository;
import com.goodee.coreconnect.chat.service.ChatRoomService;
import com.goodee.coreconnect.common.entity.Notification;
import com.goodee.coreconnect.common.notification.dto.NotificationPayload;
import com.goodee.coreconnect.common.notification.enums.NotificationType;
import com.goodee.coreconnect.common.notification.service.NotificationService;
import com.goodee.coreconnect.common.notification.service.WebSocketDeliveryService;
import com.goodee.coreconnect.support.AbstractIntegrationTest;
import com.goodee.coreconnect.user.entity.User;
import com.goodee.coreconnect.user.enums.Role;
import com.goodee.coreconnect.user.repository.UserRepository;

/**
 * 실시간 알림에 화면이 쓰는 값이 모두 담기는지 (PIT가 놓친 코드)
 */
class NotificationPayloadTest extends AbstractIntegrationTest {

  @SpyBean private WebSocketDeliveryService webSocketDeliveryService;
  @Autowired private NotificationService notificationService;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private ChatRoomService chatRoomService;
  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("한 명 알림: 보낸 사람, 받는 사람, 방, 메시지 값이 전달되고 전송 완료로 저장된다")
  void single_payloadHasAllValues() {
    User sender = newUser("sender");
    User receiver = newUser("receiver");
    ChatRoom room = chatRoomService.createChatRoom("방", List.of(sender.getId(), receiver.getId()), sender.getEmail());
    Chat chat = chatRoomService.sendChatMessage(room.getId(), sender.getId(), "안녕");

    notificationService.sendNotification(receiver.getId(), NotificationType.CHAT, "알림", chat.getId(), room.getId(),
        sender.getId(), sender.getName(), null);

    NotificationPayload payload = deliveredTo(receiver);
    assertAllValues(payload, sender, receiver, chat, room);
    Notification saved = notificationRepository.findById(payload.getNotificationId()).orElseThrow();
    assertThat(saved.getNotificationSentYn()).as("전송 완료 표시").isTrue();
  }

  @Test
  @DisplayName("메일 알림에 넘어온 채팅 번호와 방 번호는 버린다")
  void single_emailIgnoresChatAndRoom() {
    User sender = newUser("sender");
    User receiver = newUser("receiver");
    ChatRoom room = chatRoomService.createChatRoom("방", List.of(sender.getId(), receiver.getId()), sender.getEmail());
    Chat chat = chatRoomService.sendChatMessage(room.getId(), sender.getId(), "안녕");

    notificationService.sendNotification(receiver.getId(), NotificationType.EMAIL, "메일", chat.getId(), room.getId(),
        sender.getId(), sender.getName(), null);

    NotificationPayload payload = deliveredTo(receiver);
    assertThat(payload.getChatId()).isNull();
    assertThat(payload.getRoomId()).isNull();
  }

  @Test
  @DisplayName("여러 명 알림: 받는 사람마다 값이 모두 전달되고 전송 완료로 저장된다")
  void multiple_payloadHasAllValues() {
    User sender = newUser("sender");
    User receiver = newUser("receiver");
    ChatRoom room = chatRoomService.createChatRoom("방", List.of(sender.getId(), receiver.getId()), sender.getEmail());
    Chat chat = chatRoomService.sendChatMessage(room.getId(), sender.getId(), "안녕");

    notificationService.sendNotificationToUsers(List.of(receiver.getId()), NotificationType.CHAT, "알림", chat.getId(),
        room.getId(), sender.getId(), sender.getName(), null, null);

    NotificationPayload payload = deliveredTo(receiver);
    assertAllValues(payload, sender, receiver, chat, room);
    Notification saved = notificationRepository.findById(payload.getNotificationId()).orElseThrow();
    assertThat(saved.getNotificationSentYn()).as("전송 완료 표시").isTrue();
  }

  private NotificationPayload deliveredTo(User receiver) {
    ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
    verify(webSocketDeliveryService, timeout(5000)).sendToUser(eq(receiver.getId()), payload.capture());
    return (NotificationPayload) payload.getValue();
  }

  private void assertAllValues(NotificationPayload payload, User sender, User receiver, Chat chat, ChatRoom room) {
    assertThat(payload.getNotificationId()).isNotNull();
    assertThat(payload.getSenderId()).isEqualTo(sender.getId());
    assertThat(payload.getSenderName()).isEqualTo(sender.getName());
    assertThat(payload.getRecipientId()).isEqualTo(receiver.getId());
    assertThat(payload.getReceiverName()).isEqualTo(receiver.getName());
    assertThat(payload.getMessage()).isEqualTo("알림");
    assertThat(payload.getNotificationType()).isEqualTo("CHAT");
    assertThat(payload.getChatId()).isEqualTo(chat.getId());
    assertThat(payload.getRoomId()).isEqualTo(room.getId());
    assertThat(payload.getCreatedAt()).isNotNull();
  }

  private User newUser(String prefix) {
    String email = prefix + "-" + UUID.randomUUID() + "@test.com";
    return userRepository.save(User.createUser("pw", prefix, Role.USER, email, null, null, null, null));
  }
}
