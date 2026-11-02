package com.goodee.coreconnect.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import com.goodee.coreconnect.attendance.repository.AttendanceRepository;
import com.goodee.coreconnect.attendance.service.AttendanceService;
import com.goodee.coreconnect.support.AbstractIntegrationTest;
import com.goodee.coreconnect.user.entity.User;
import com.goodee.coreconnect.user.enums.Role;
import com.goodee.coreconnect.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 출근 동시성 결과 수치 측정용. 동시 요청이 몇 개든 저장 1건·성공 1번·나머지는 "이미 출근"인지 함께 검사
 *
 * 평소 테스트 실행에서는 건너뛰고, 환경변수 MEASURE=true일 때만 실행
 * 동시 요청 10·50·100개를 각각 5번씩 반복해 저장 건수, 성공/실패 수, 평균 처리 시간을 로그로 남김
 */
@Slf4j
@Tag("measure")
@EnabledIfEnvironmentVariable(named = "MEASURE", matches = "true")
class AttendanceCheckInMeasurementTest extends AbstractIntegrationTest {

  private static final int[] THREAD_COUNTS = {10, 50, 100};
  private static final int REPEAT = 5;

  @Autowired
  private AttendanceService attendanceService;

  @Autowired
  private AttendanceRepository attendanceRepository;

  @Autowired
  private UserRepository userRepository;

  @Test
  @DisplayName("동시 출근 요청 수치 측정")
  void measure() throws InterruptedException {
    List<String> rows = new ArrayList<>();
    for (int threads : THREAD_COUNTS) {
      for (int round = 1; round <= REPEAT; round++) {
        rows.add(threads + "," + round + "," + runOnce(threads));
      }
    }
    log.info("MEASURE_RESULT threads,round,saved,success,alreadyCheckedIn,incorrectResultSize,other,avgMs,maxMs");
    rows.forEach(r -> log.info("MEASURE_RESULT {}", r));
  }

  private String runOnce(int threadCount) throws InterruptedException {
    String email = "measure-" + UUID.randomUUID() + "@test.com";
    User user = userRepository.save(User.createUser("pw", "측정용", Role.USER, email, null, null, null, null));

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threadCount);

    AtomicInteger success = new AtomicInteger();
    AtomicInteger alreadyCheckedIn = new AtomicInteger();    // "이미 오늘 출근 기록이 있습니다."
    AtomicInteger incorrectResultSize = new AtomicInteger(); // "결과가 여러 건" 서버 오류
    AtomicInteger other = new AtomicInteger();
    AtomicLong totalNanos = new AtomicLong();
    AtomicLong maxNanos = new AtomicLong();

    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> {
        ready.countDown();
        long begin = 0;
        try {
          start.await();
          begin = System.nanoTime();
          attendanceService.checkIn(email);
          success.incrementAndGet();
        } catch (IllegalStateException e) {
          alreadyCheckedIn.incrementAndGet();
        } catch (IncorrectResultSizeDataAccessException e) {
          incorrectResultSize.incrementAndGet();
        } catch (Throwable e) {
          other.incrementAndGet();
          log.warn("기타 예외: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        } finally {
          long elapsed = System.nanoTime() - begin;
          totalNanos.addAndGet(elapsed);
          maxNanos.accumulateAndGet(elapsed, Math::max);
          done.countDown();
        }
      });
    }

    ready.await();
    start.countDown();
    boolean finished = done.await(120, TimeUnit.SECONDS);
    executor.shutdown();

    LocalDate today = LocalDate.now();
    var saved = attendanceRepository.findByUser_IdAndWorkDateBetween(user.getId(), today, today);
    attendanceRepository.deleteAll(saved);
    userRepository.deleteById(user.getId());

    assertThat(finished).as("%d개 요청이 120초 안에 끝남", threadCount).isTrue();
    // 오류 수를 먼저 봄: 뒤의 합계 판정이 먼저 실패하면 어떤 오류인지 메시지에 안 나옴
    assertThat(incorrectResultSize.get()).as("결과 여러 건 오류").isZero();
    assertThat(other.get()).as("기타 예외").isZero();
    assertThat(saved).as("%d개 동시 요청에도 저장은 1건", threadCount).hasSize(1);
    assertThat(success.get()).as("성공 1번").isEqualTo(1);
    assertThat(alreadyCheckedIn.get()).as("나머지는 이미 출근").isEqualTo(threadCount - 1);

    double avgMs = totalNanos.get() / 1_000_000.0 / threadCount;
    double maxMs = maxNanos.get() / 1_000_000.0;
    return String.format("%d,%d,%d,%d,%d,%.1f,%.1f",
        saved.size(), success.get(), alreadyCheckedIn.get(), incorrectResultSize.get(), other.get(), avgMs, maxMs);
  }
}
