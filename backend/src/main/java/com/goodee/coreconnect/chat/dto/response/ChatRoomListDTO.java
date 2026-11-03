package com.goodee.coreconnect.chat.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//채팅방 목록에서: 방 id, 이름, 마지막 메시지 내용/시간, 마지막 보낸사람, 안읽은 메시지수 표시
@Getter
@Setter
@NoArgsConstructor
public class ChatRoomListDTO {
	private Integer roomId;
	private String roomName;
	private String lastMessageContent;
	private LocalDateTime lasMessageTime;
	private String lastSenderName;
	private long unreadCount;
	private Boolean lastMessageFileYn; // 마지막 메시지의 파일 첨부 여부
	private int memberCount; // 나를 포함한 참여자 수 (2명이면 1:1)
	private List<ChatRoomMemberPreviewDTO> members; // 나를 뺀 참여자, 최대 4명 (목록 프로필 사진용)

	public ChatRoomListDTO(Integer roomId, String roomName, String lastMessageContent, LocalDateTime lasMessageTime,
			String lastSenderName, long unreadCount, Boolean lastMessageFileYn) {
		this.roomId = roomId;
		this.roomName = roomName;
		this.lastMessageContent = lastMessageContent;
		this.lasMessageTime = lasMessageTime;
		this.lastSenderName = lastSenderName;
		this.unreadCount = unreadCount;
		this.lastMessageFileYn = lastMessageFileYn;
	}
}
