import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import NotificationPopover from "./NotificationPopover";
import {
  getBellUnreadNotifications,
  getNotificationPage,
  markNotificationAsRead,
} from "../../../../features/notification/api/notificationAPI";

vi.mock("../../../../features/notification/api/notificationAPI", () => ({
  getBellUnreadNotifications: vi.fn(),
  getNotificationPage: vi.fn(),
  markNotificationAsRead: vi.fn(),
  markAllNotificationsAsRead: vi.fn(),
}));

const unreadA = { notificationId: 1, notificationType: "EMAIL", message: "새 메일 A", sentAt: "2026-11-01T10:00:00" };
const unreadB = { notificationId: 2, notificationType: "EMAIL", message: "새 메일 B", sentAt: "2026-11-01T09:00:00" };
const readC = { notificationId: 3, notificationType: "EMAIL", message: "읽은 메일 C", sentAt: "2026-11-01T08:00:00", readYn: true };

function renderPopover(props = {}) {
  return render(
    <MemoryRouter>
      <NotificationPopover anchorEl={document.body} open onClose={() => {}} {...props} />
    </MemoryRouter>
  );
}

describe("NotificationPopover", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, "log").mockImplementation(() => {});
    getBellUnreadNotifications.mockResolvedValue([unreadA, unreadB]);
    markNotificationAsRead.mockResolvedValue({});
  });

  it("안 읽은 알림 탭에 개수를 보여 준다", async () => {
    renderPopover();

    expect(await screen.findByRole("tab", { name: "안 읽은 알림 (2)" })).toBeInTheDocument();
    expect(screen.getByText("새 메일 A")).toBeInTheDocument();
  });

  it("안 읽은 알림을 누르면 목록에서 빠지고 개수가 1 줄어든다", async () => {
    const onCount = vi.fn();
    const user = userEvent.setup();
    renderPopover({ onNotificationCountChange: onCount });

    await user.click(await screen.findByText("새 메일 A"));

    expect(screen.queryByText("새 메일 A")).not.toBeInTheDocument();
    expect(onCount).toHaveBeenLastCalledWith(1);
    expect(markNotificationAsRead).toHaveBeenCalledWith(1);
  });

  it("전체 탭: 읽은 알림에만 읽음 칩, 다음 쪽이 있을 때만 더 보기", async () => {
    getNotificationPage.mockResolvedValueOnce({ content: [unreadA, readC], last: false });
    getNotificationPage.mockResolvedValueOnce({ content: [{ ...unreadB }], last: true });
    const user = userEvent.setup();
    renderPopover();

    await user.click(await screen.findByRole("tab", { name: "전체 알림" }));

    expect(await screen.findByText("읽은 메일 C")).toBeInTheDocument();
    expect(screen.getAllByText("읽음")).toHaveLength(1);

    await user.click(screen.getByRole("button", { name: "더 보기" }));

    expect(await screen.findByText("새 메일 B")).toBeInTheDocument();
    expect(getNotificationPage).toHaveBeenLastCalledWith("all", 1, 20);
    expect(screen.queryByRole("button", { name: "더 보기" })).not.toBeInTheDocument();
  });
});
