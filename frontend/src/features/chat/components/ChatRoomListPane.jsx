import React from 'react';
import { Avatar, Box, Tabs, Tab, List, ListItem, ListItemButton, ListItemText, Badge, Typography } from "@mui/material";

// 방 프로필: 1:1(참여자 2명 이하)은 상대 사진 1개, 단체방은 나를 뺀 참여자 사진을 최대 4개까지 칸으로 나눠 보여준다
function RoomAvatar({ room }) {
  const members = Array.isArray(room.members) ? room.members : [];
  const avatarOf = (member, size, fontSize) => (
    <Avatar
      key={member.userId}
      src={member.profileImageUrl || undefined}
      alt={member.name}
      sx={{ width: size, height: size, fontSize, bgcolor: "#4a90e2" }}
    >
      {member.name?.charAt(0) || "?"}
    </Avatar>
  );

  if ((room.memberCount ?? 0) <= 2 || members.length <= 1) {
    return (
      <Box data-testid="room-avatar" data-kind="direct" sx={{ width: 48, height: 48, flexShrink: 0, display: "flex", alignItems: "center", justifyContent: "center" }}>
        {members[0] ? avatarOf(members[0], 48, 18) : <Avatar sx={{ width: 48, height: 48 }} />}
      </Box>
    );
  }
  return (
    <Box data-testid="room-avatar" data-kind="group" sx={{
      width: 48, height: 48, flexShrink: 0, display: "grid", gap: "2px",
      gridTemplateColumns: "1fr 1fr", gridTemplateRows: members.length > 2 ? "1fr 1fr" : "1fr",
      alignContent: "center", justifyItems: "center", alignItems: "center",
    }}>
      {members.slice(0, 4).map(m => avatarOf(m, 23, 11))}
    </Box>
  );
}

// 채팅방 목록(좌측) & 탭
function ChatRoomListPane({
  tabIdx, setTabIdx, roomList, selectedRoomId, setSelectedRoomId, formatTime, highlightedRoomId
}) {
  // 정렬 상태: 기본값은 최신 메시지부터 (내림차순)
  const sortOrder = 'desc';
  
  // 채팅방 목록의 시간 표시: "오후 09시 00분", 오늘이 아니면 앞에 날짜를 붙인다
  const formatLastMessageTime = (time) => {
    const formatted = formatTime(time);
    if (!formatted) return "";
    const today = formatted.match(/^(오전|오후) (\d{2}):(\d{2})$/);
    if (today) return `${today[1]} ${today[2]}시 ${today[3]}분`;
    const past = formatted.match(/^(\d{4}-\d{2}-\d{2}) (\d{2}):(\d{2})/);
    if (!past) return formatted;
    const hour = Number(past[2]);
    const hour12 = String(hour % 12 || 12).padStart(2, "0");
    return `${past[1]} ${hour < 12 ? "오전" : "오후"} ${hour12}시 ${past[3]}분`;
  };
  // "전체"(0) 또는 "안읽음"(1) 탭 필터링
  const filteredRooms = tabIdx === 0 ? roomList : roomList.filter(r => r.unreadCount > 0);

  // 마지막 메시지 시간 기준 정렬 함수
  const sortRoomsByLastMessage = (rooms) => {
    return [...rooms].sort((a, b) => {
      const timeA = a.lasMessageTime ? new Date(a.lasMessageTime).getTime() : 0;
      const timeB = b.lasMessageTime ? new Date(b.lasMessageTime).getTime() : 0;
      
      if (sortOrder === 'asc') {
        // 오름차순: 오래된 메시지부터 (시간이 작은 것부터)
        return timeA - timeB;
      } else {
        // 내림차순: 최신 메시지부터 (시간이 큰 것부터)
        return timeB - timeA;
      }
    });
  };

  // 정렬된 채팅방 목록
  const sortedRooms = sortRoomsByLastMessage(filteredRooms);

  return (
    <Box sx={{
      flex: "0 0 420px", minWidth: 350, maxWidth: 470,
      height: "calc(100vh - 56px - 32px)", background: "#fff",
      borderRight: "1px solid #e3e8ef", borderRadius: 0,
      display: "flex", flexDirection: "column", p: 0,
    }}>
      {/* 탭 영역 */}
      <Box sx={{ px: 0, pt: 0, pb: 1, position: 'relative' }}>
        <Tabs
          value={tabIdx}
          onChange={(_, v) => setTabIdx(v)}
          variant="fullWidth"
          sx={{
            borderBottom: "1px solid #e3e8ef",
            background: "#f9fafb",
            mb: 0,
            minHeight: 44
          }}
        >
          <Tab label="전체" sx={{ fontWeight: 700, fontSize: 17, minHeight: 44 }} />
          <Tab label="안읽음" sx={{ fontWeight: 700, fontSize: 17, minHeight: 44 }} />
        </Tabs>
      </Box>
      {/* 방 목록 */}
      <List
        sx={{
          overflowY: "auto",
          flex: 1,
          px: 2,
          bgcolor: "#fff",
          scrollbarWidth: "thin",
          "&::-webkit-scrollbar": { width: 8 },
          "&::-webkit-scrollbar-thumb": {
            background: "#e4eaf3",
            borderRadius: "7px"
          },
          "&::-webkit-scrollbar-track": { background: "#fff" }
        }}
      >
        {filteredRooms.length === 0 ? (
          <ListItem>
            <ListItemText
              primary={
                <Box sx={{ width: "100%", textAlign: "center", color: 'text.disabled', fontSize: 15 }}>
                  {tabIdx === 1 ? "안읽은 메시지가 있는 채팅방이 없습니다." : "채팅방을 생성해서 대화를 시작해보세요"}
                </Box>
              }
            />
          </ListItem>
        ) : (
          sortedRooms.map(room => (
            <ListItem
              key={room.roomId}
              disablePadding
              sx={{
                mb: 1,
                borderBottom: "1px solid #e9eaeb"
              }}
            >
              <ListItemButton
                selected={selectedRoomId === room.roomId}
                onClick={() => setSelectedRoomId(room.roomId)}
                alignItems="center"
                sx={{
                  borderRadius: 0,
                  background: selectedRoomId === room.roomId ? "#f3f6fa" : "#fff",
                  py: 2.4,
                  px: 1.2,
                  minHeight: "64px",
                  cursor: "pointer",
                  // 새로 생성된 방 하이라이팅
                  ...(highlightedRoomId === room.roomId && {
                    background: "#e3f2fd",
                    borderLeft: "4px solid #1976d2",
                    animation: "highlightPulse 2s ease-in-out",
                    "@keyframes highlightPulse": {
                      "0%": { background: "#e3f2fd" },
                      "50%": { background: "#bbdefb" },
                      "100%": { background: "#e3f2fd" }
                    }
                  })
                }}
              >
                <RoomAvatar room={room} />
                <ListItemText
                  sx={{ ml: 1.5, my: 0, minWidth: 0 }}
                  primary={
                    <Typography sx={{ fontWeight: 700, fontSize: 18, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {room.roomName}
                    </Typography>
                  }
                  secondary={
                    <Box component="span" sx={{ display: "block" }}>
                      {/* 초대된 채팅방 표시 */}
                      {room.invited && (
                        <Typography
                          component="span"
                          sx={{
                            fontSize: 12,
                            color: "#1976d2",
                            fontWeight: 600,
                            display: "block",
                            mb: 0.5
                          }}
                        >
                          내가 초대된 채팅방입니다
                        </Typography>
                      )}
                      <Typography
                        component="span"
                        sx={{
                          fontSize: 14,
                          color: "#555",
                          whiteSpace: "nowrap",
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          display: "block"
                        }}
                      >
                        {room.lastMessageFileYn ? "이미지 업로드" : (room.lastMessageContent ? room.lastMessageContent : "")}
                      </Typography>
                    </Box>
                  }
                />
                {/* 오른쪽: 마지막 메시지 날짜·시간, 그 아래 안 읽은 수 */}
                <Box sx={{ flexShrink: 0, ml: 1, display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 1 }}>
                  <Typography data-testid="room-last-time" sx={{ fontSize: 12, color: "#9495a0", whiteSpace: "nowrap" }}>
                    {room.lasMessageTime ? formatLastMessageTime(room.lasMessageTime) : ""}
                  </Typography>
                  {room.unreadCount > 0 && (
                    <Badge
                      badgeContent={room.unreadCount}
                      sx={{
                        mr: 1.5,
                        "& .MuiBadge-badge": {
                          position: "static",
                          transform: "none",
                          background: "#f44336",
                          color: "#fff",
                          fontWeight: 700,
                          fontSize: "13px",
                          borderRadius: "10px",
                          minWidth: "20px",
                        }
                      }}
                    />
                  )}
                </Box>
              </ListItemButton>
            </ListItem>
          ))
        )}
      </List>
    </Box>
  );
}

export default ChatRoomListPane;