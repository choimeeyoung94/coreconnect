package com.goodee.coreconnect.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.goodee.coreconnect.chat.entity.ChatRoom;
import com.goodee.coreconnect.chat.service.ChatRoomService;
import com.goodee.coreconnect.support.AbstractIntegrationTest;
import com.goodee.coreconnect.user.entity.User;
import com.goodee.coreconnect.user.enums.Role;
import com.goodee.coreconnect.user.repository.UserRepository;

/**
 * 방을 만들 때 만든 사람이 참여자 목록에 들어가는지
 *
 * 화면은 만든 사람을 자동으로 넣어 보내지만, API를 직접 부르면 빠질 수 있었음
 * (만든 사람이 자기 방을 목록에서 못 보고, 2명 방이 "group"으로 저장됨)
 */
class ChatRoomCreateMembersTest extends AbstractIntegrationTest {

  @Autowired private ChatRoomService chatRoomService;
  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("상대 한 명만 보내도 만든 사람이 참여자가 되고 1:1 방(alone)으로 저장된다")
  void onlyOtherUser_creatorJoins() {
    User me = newUser("me");
    User other = newUser("other");

    ChatRoom room = chatRoomService.createChatRoom("1대1", List.of(other.getId()), me.getEmail());

    assertThat(chatRoomService.getParticipantIds(room.getId())).containsExactlyInAnyOrder(me.getId(), other.getId());
    assertThat(room.getRoomType()).isEqualTo("alone");
    assertThat(chatRoomService.getChatRoomListWithUnreadCount(me.getId()))
        .as("만든 사람의 방 목록").anyMatch(r -> r.getRoomId().equals(room.getId()));
  }

  @Test
  @DisplayName("화면처럼 만든 사람을 함께 보내도 참여자는 한 번만 들어간다")
  void creatorIncluded_noDuplicate() {
    User me = newUser("me");
    User other = newUser("other");

    ChatRoom room = chatRoomService.createChatRoom("1대1", List.of(me.getId(), other.getId()), me.getEmail());

    assertThat(chatRoomService.getParticipantIds(room.getId())).containsExactlyInAnyOrder(me.getId(), other.getId());
    assertThat(room.getRoomType()).isEqualTo("alone");
  }

  @Test
  @DisplayName("만든 사람 포함 3명 이상이면 단체방(group)이다")
  void threeMembers_isGroup() {
    User me = newUser("me");
    User b = newUser("b");
    User c = newUser("c");

    ChatRoom room = chatRoomService.createChatRoom("단체", List.of(b.getId(), c.getId()), me.getEmail());

    assertThat(chatRoomService.getParticipantIds(room.getId())).hasSize(3);
    assertThat(room.getRoomType()).isEqualTo("group");
  }

  private User newUser(String prefix) {
    String email = prefix + "-" + UUID.randomUUID() + "@test.com";
    return userRepository.save(User.createUser("pw", prefix, Role.USER, email, null, null, null, null));
  }
}
