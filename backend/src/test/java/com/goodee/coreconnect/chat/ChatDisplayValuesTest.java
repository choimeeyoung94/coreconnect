package com.goodee.coreconnect.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.goodee.coreconnect.chat.dto.response.ChatResponseDTO;
import com.goodee.coreconnect.chat.dto.response.ChatRoomListDTO;
import com.goodee.coreconnect.chat.entity.Chat;
import com.goodee.coreconnect.chat.entity.ChatRoom;
import com.goodee.coreconnect.chat.pubsub.ChatPubSubService;
import com.goodee.coreconnect.chat.repository.ChatRepository;
import com.goodee.coreconnect.chat.service.ChatRoomService;
import com.goodee.coreconnect.support.AbstractIntegrationTest;
import com.goodee.coreconnect.user.entity.User;
import com.goodee.coreconnect.user.enums.Role;
import com.goodee.coreconnect.user.repository.UserRepository;

/**
 * 화면에 보여 주는 값이 DB와 방송에 실제로 담기는지 (PIT가 놓친 코드)
 */
class ChatDisplayValuesTest extends AbstractIntegrationTest {

  @SpyBean private ChatPubSubService chatPubSubService;
  @Autowired private ChatRoomService chatRoomService;
  @Autowired private ChatRepository chatRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("메시지를 보내면 보낸 사람을 뺀 안 읽은 수가 메시지에 저장된다")
  void sendMessage_savesUnreadCount() {
    User a = newUser("a");
    User b = newUser("b");
    User c = newUser("c");
    ChatRoom room = newRoom(a, b, c);

    Chat chat = chatRoomService.sendChatMessage(room.getId(), a.getId(), "안녕");

    assertThat(chatRepository.findById(chat.getId()).orElseThrow().getUnreadCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("방 목록은 방 이름과 마지막 메시지의 내용, 시각, 보낸 사람, 파일 여부를 담는다")
  void roomList_hasLastMessageValues() {
    User a = newUser("a");
    User b = newUser("b");
    ChatRoom room = newRoom(a, b);
    Chat chat = chatRoomService.sendChatMessage(room.getId(), a.getId(), "마지막 메시지");

    ChatRoomListDTO dto = roomInList(b, room);

    assertThat(dto.getRoomName()).isEqualTo("방");
    assertThat(dto.getLastMessageContent()).isEqualTo("마지막 메시지");
    assertThat(dto.getLasMessageTime()).isNotNull();
    assertThat(dto.getLastSenderName()).isEqualTo(a.getName());
    assertThat(dto.getLastMessageFileYn()).isEqualTo(chatRepository.findById(chat.getId()).orElseThrow().getFileYn());
    assertThat(dto.getLastMessageFileYn()).isNotNull();
  }

  @Test
  @DisplayName("메시지가 없는 방은 마지막 메시지 값이 모두 비어 있다")
  void roomList_noMessage_isEmpty() {
    User a = newUser("a");
    User b = newUser("b");
    ChatRoom room = newRoom(a, b);

    ChatRoomListDTO dto = roomInList(b, room);

    assertThat(dto.getLastMessageContent()).isNull();
    assertThat(dto.getLasMessageTime()).isNull();
    assertThat(dto.getLastSenderName()).isNull();
    assertThat(dto.getLastMessageFileYn()).isNull();
  }

  @Test
  @DisplayName("들어온 시각이 비어 있는 예전 참여자에게도 마지막 메시지가 보인다")
  void roomList_nullJoinedAt_showsLastMessage() {
    User a = newUser("a");
    User b = newUser("b");
    ChatRoom room = newRoom(a, b);
    chatRoomService.sendChatMessage(room.getId(), a.getId(), "옛 메시지");
    setJoinedAt(b, room, null);

    assertThat(roomInList(b, room).getLastMessageContent()).isEqualTo("옛 메시지");
  }

  @Test
  @DisplayName("들어오기 전에 온 메시지는 방 목록 미리보기에 보이지 않는다")
  void roomList_beforeJoin_hidesLastMessage() {
    User a = newUser("a");
    User b = newUser("b");
    ChatRoom room = newRoom(a, b);
    chatRoomService.sendChatMessage(room.getId(), a.getId(), "들어오기 전 메시지");
    setJoinedAt(b, room, "2999-01-01 00:00:00");

    assertThat(roomInList(b, room).getLastMessageContent()).isNull();
  }

  @Test
  @DisplayName("나가면 방에 나감 안내가 방송되고 보낸 사람 정보가 담긴다")
  void leave_broadcastsNotice() {
    User a = newUser("a");
    User b = newUser("b");
    ChatRoom room = newRoom(a, b);

    chatRoomService.leaveChatRoom(room.getId(), b.getEmail());

    ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
    verify(chatPubSubService, atLeastOnce()).publish(eq("/topic/chat.room." + room.getId()), payload.capture());
    ChatResponseDTO notice = payload.getAllValues().stream()
        .filter(ChatResponseDTO.class::isInstance).map(ChatResponseDTO.class::cast)
        .filter(dto -> (b.getName() + "님이 채팅방을 나갔습니다").equals(dto.getMessageContent()))
        .findFirst().orElseThrow();
    assertThat(notice.getId()).isNotNull();
    assertThat(notice.getRoomId()).isEqualTo(room.getId());
    assertThat(notice.getSenderId()).isEqualTo(b.getId());
    assertThat(notice.getSenderName()).isEqualTo(b.getName());
    assertThat(notice.getSenderEmail()).isEqualTo(b.getEmail());
    assertThat(notice.getSendAt()).isNotNull();
    assertThat(notice.getFileYn()).isNotNull();
    assertThat(notice.getUnreadCount()).isZero();
    assertThat(chatRepository.findById(notice.getId()).orElseThrow().getSendAt().toString())
        .startsWith(notice.getSendAt().substring(0, 16));
  }

  private ChatRoom newRoom(User... users) {
    List<Integer> ids = java.util.Arrays.stream(users).map(User::getId).toList();
    return chatRoomService.createChatRoom("방", ids, users[0].getEmail());
  }

  private ChatRoomListDTO roomInList(User user, ChatRoom room) {
    return chatRoomService.getChatRoomListWithUnreadCount(user.getId()).stream()
        .filter(r -> r.getRoomId().equals(room.getId())).findFirst().orElseThrow();
  }

  private void setJoinedAt(User user, ChatRoom room, String joinedAt) {
    jdbcTemplate.update("UPDATE chat_room_user SET joined_at = ? WHERE user_id = ? AND chat_room_id = ?",
        joinedAt, user.getId(), room.getId());
  }

  private User newUser(String prefix) {
    String email = prefix + "-" + UUID.randomUUID() + "@test.com";
    return userRepository.save(User.createUser("pw", prefix, Role.USER, email, null, null, null, null));
  }
}
