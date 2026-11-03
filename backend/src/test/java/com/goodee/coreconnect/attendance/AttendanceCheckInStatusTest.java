package com.goodee.coreconnect.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.goodee.coreconnect.attendance.entity.Attendance;
import com.goodee.coreconnect.attendance.enums.AttendanceStatus;
import com.goodee.coreconnect.attendance.repository.AttendanceRepository;
import com.goodee.coreconnect.attendance.service.AttendanceServiceImpl;
import com.goodee.coreconnect.leave.repository.LeaveRequestRepository;
import com.goodee.coreconnect.user.entity.User;
import com.goodee.coreconnect.user.enums.Role;
import com.goodee.coreconnect.user.repository.UserRepository;

/**
 * checkIn()이 저장하는 값 단위 테스트 (시각을 고정해 지각 경계를 확인)
 */
@ExtendWith(MockitoExtension.class)
class AttendanceCheckInStatusTest {

  private static final String EMAIL = "user@test.com";

  @Mock
  private AttendanceRepository attendanceRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private LeaveRequestRepository leaveRequestRepository;

  @InjectMocks
  private AttendanceServiceImpl attendanceService;

  @ParameterizedTest
  @CsvSource({
      "2026-11-02T08:59:59, PRESENT",
      "2026-11-02T09:00:00, PRESENT",
      "2026-11-02T09:00:01, LATE",
  })
  @DisplayName("9시까지는 정상, 9시를 넘으면 지각으로 저장하고 시각도 함께 저장한다")
  void savesStatusAndTimes(LocalDateTime now, AttendanceStatus expected) {
    given(attendanceRepository.findByUser_EmailAndWorkDate(eq(EMAIL), any())).willReturn(Optional.empty());
    given(userRepository.findByEmail(EMAIL))
        .willReturn(Optional.of(User.createUser("pw", "테스터", Role.USER, EMAIL, null, null, null, null)));

    try (MockedStatic<LocalDateTime> clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
      clock.when(LocalDateTime::now).thenReturn(now);
      attendanceService.checkIn(EMAIL);
    }

    ArgumentCaptor<Attendance> saved = ArgumentCaptor.forClass(Attendance.class);
    verify(attendanceRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(expected);
    assertThat(saved.getValue().getCheckIn()).isEqualTo(now);
    assertThat(saved.getValue().getCreatedAt()).isEqualTo(now);
    assertThat(saved.getValue().getUpdatedAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("오늘 출근 기록이 이미 있으면 저장을 시도하지 않고 거절한다")
  void alreadyCheckedIn_doesNotSave() {
    given(attendanceRepository.findByUser_EmailAndWorkDate(eq(EMAIL), any()))
        .willReturn(Optional.of(mock(Attendance.class)));

    assertThatThrownBy(() -> attendanceService.checkIn(EMAIL))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("이미 오늘 출근 기록이 있습니다.");
    verify(attendanceRepository, never()).saveAndFlush(any());
  }
}
