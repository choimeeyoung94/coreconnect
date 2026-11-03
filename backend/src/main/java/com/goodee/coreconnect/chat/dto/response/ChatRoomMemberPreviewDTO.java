package com.goodee.coreconnect.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 채팅방 목록의 프로필 사진에 쓰는 참여자 요약 (나를 뺀 참여자)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomMemberPreviewDTO {
	private Integer userId;
	private String name;
	private String profileImageUrl;
}
