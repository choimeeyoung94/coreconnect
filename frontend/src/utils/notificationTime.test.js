import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { formatNotificationTime } from "./notificationTime";

describe("formatNotificationTime", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-11-01T12:00:00+09:00"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it.each([
    ["2026-11-01T11:59:30", "방금 전"],
    ["2026-11-01T11:30:00", "30분 전"],
    ["2026-11-01T09:00:00", "3시간 전"],
    ["2026-10-30T12:00:00", "2일 전"],
    ["2026-10-17T12:00:00", "10월 17일"],
    ["2025-10-17T12:00:00", "2025년 10월 17일"],
  ])("%s → %s", (sentAt, expected) => {
    expect(formatNotificationTime(sentAt)).toBe(expected);
  });

  it("시간대가 없으면 한국 시간으로 읽고, 있으면 적힌 시간대를 따른다", () => {
    expect(formatNotificationTime("2026-11-01 11:30:00")).toBe("30분 전");
    expect(formatNotificationTime("2026-11-01T02:30:00Z")).toBe("30분 전");
  });

  it("값이 없거나 날짜가 아니면 빈 문자열", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    expect(formatNotificationTime(null)).toBe("");
    expect(formatNotificationTime("날짜아님")).toBe("");
    warn.mockRestore();
  });
});
