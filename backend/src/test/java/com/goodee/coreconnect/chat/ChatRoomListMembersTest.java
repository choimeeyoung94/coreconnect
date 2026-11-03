package com.goodee.coreconnect.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.goodee.coreconnect.chat.dto.response.ChatRoomListDTO;
import com.goodee.coreconnect.chat.dto.response.ChatRoomMemberPreviewDTO;
import com.goodee.coreconnect.chat.entity.ChatRoom;
import com.goodee.coreconnect.chat.service.ChatRoomService;
import com.goodee.coreconnect.support.AbstractIntegrationTest;
import com.goodee.coreconnect.user.entity.User;
import com.goodee.coreconnect.user.enums.Role;
import com.goodee.coreconnect.user.repository.UserRepository;

/**
 * 채팅방 목록이 프로필 사진에 쓸 참여자 수와 참여자(나 제외, 최대 4명)를 함께 주는지
 */
class ChatRoomListMembersTest extends AbstractIntegrationTest {

  @Autowired private ChatRoomService chatRoomService;
  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("1:1 방은 참여자 수 2와 상대 한 명을 준다")
  void directRoom_hasOtherMember() {
    User me = newUser("me");
    User other = newUser("other");
    ChatRoom room = chatRoomService.createChatRoom("1대1", List.of(me.getId(), other.getId()), me.getEmail());

    ChatRoomListDTO dto = roomInList(me, room);

    assertThat(dto.getMemberCount()).isEqualTo(2);
    assertThat(dto.getMembers()).extracting(ChatRoomMemberPreviewDTO::getUserId).containsExactly(other.getId());
    assertThat(dto.getMembers().get(0).getName()).isEqualTo(other.getName());
    assertThat(dto.getMembers().get(0).getProfileImageUrl()).as("사진 없는 사용자").isEmpty();
  }

  @Test
  @DisplayName("단체방은 전체 참여자 수와 나를 뺀 참여자를 최대 4명까지 준다")
  void groupRoom_hasUpToFourOthers() {
    User me = newUser("me");
    List<Integer> others = new ArrayList<>();
    List<Integer> members = new ArrayList<>(List.of(me.getId()));
    for (int i = 0; i < 5; i++) {
      others.add(newUser("u" + i).getId());
    }
    members.addAll(others);
    ChatRoom room = chatRoomService.createChatRoom("단체", members, me.getEmail());

    ChatRoomListDTO dto = roomInList(me, room);

    assertThat(dto.getMemberCount()).isEqualTo(6);
    assertThat(dto.getMembers()).hasSize(4);
    assertThat(dto.getMembers()).extracting(ChatRoomMemberPreviewDTO::getUserId)
        .doesNotContain(me.getId()).isSubsetOf(others);
  }

  @Test
  @DisplayName("같은 방이라도 보는 사람마다 자기를 뺀 참여자를 받는다")
  void eachViewerSeesOthers() {
    User a = newUser("a");
    User b = newUser("b");
    ChatRoom room = chatRoomService.createChatRoom("1대1", List.of(a.getId(), b.getId()), a.getEmail());

    assertThat(roomInList(a, room).getMembers()).extracting(ChatRoomMemberPreviewDTO::getUserId).containsExactly(b.getId());
    assertThat(roomInList(b, room).getMembers()).extracting(ChatRoomMemberPreviewDTO::getUserId).containsExactly(a.getId());
  }

  private ChatRoomListDTO roomInList(User user, ChatRoom room) {
    return chatRoomService.getChatRoomListWithUnreadCount(user.getId()).stream()
        .filter(r -> r.getRoomId().equals(room.getId())).findFirst().orElseThrow();
  }

  private User newUser(String prefix) {
    String email = prefix + "-" + UUID.randomUUID() + "@test.com";
    return userRepository.save(User.createUser("pw", prefix, Role.USER, email, null, null, null, null));
  }
}
