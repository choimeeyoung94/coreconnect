// 알림 시간 표시 함수 (상대 시간 또는 절대 시간)
export const formatNotificationTime = (sentAt) => {
  if (!sentAt) return "";

  try {
    let date;

    // ISO 8601 형식인 경우 (예: "2025-01-15T10:30:00" 또는 "2025-01-15T10:30:00+09:00")
    if (typeof sentAt === 'string' && sentAt.includes('T')) {
      // 시간대 정보가 없으면 한국 시간대(UTC+9)로 가정
      if (!sentAt.includes('+') && !sentAt.includes('Z') && !sentAt.includes('-', 10)) {
        // "2025-01-15T10:30:00" 형식인 경우 한국 시간대로 해석
        date = new Date(sentAt + '+09:00');
      } else {
        date = new Date(sentAt);
      }
    }
    // "yyyy-MM-dd HH:mm:ss" 형식인 경우 (예: "2025-01-15 10:30:00")
    else if (typeof sentAt === 'string' && sentAt.includes(' ')) {
      // 공백을 T로 변경하고 한국 시간대 추가
      const isoString = sentAt.replace(' ', 'T') + '+09:00';
      date = new Date(isoString);
    }
    // 기타 형식
    else {
      date = new Date(sentAt);
    }

    // 유효한 날짜인지 확인
    if (isNaN(date.getTime())) {
      console.warn('[formatNotificationTime] 잘못된 날짜 형식:', sentAt);
      return "";
    }

    const now = new Date();
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    // 상대 시간 표시
    if (diffMins < 1) return "방금 전";
    if (diffMins < 60) return `${diffMins}분 전`;
    if (diffHours < 24) return `${diffHours}시간 전`;
    if (diffDays < 7) return `${diffDays}일 전`;

    // 7일 이상 지난 경우 날짜 표시 ("10월 17일", 올해가 아니면 "2025년 10월 17일")
    const monthDay = `${date.getMonth() + 1}월 ${date.getDate()}일`;
    return date.getFullYear() === now.getFullYear() ? monthDay : `${date.getFullYear()}년 ${monthDay}`;
  } catch (error) {
    console.error('[formatNotificationTime] 날짜 파싱 오류:', error, 'sentAt:', sentAt);
    return "";
  }
};
