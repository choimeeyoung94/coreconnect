import { describe, it, expect, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import ChatRoomListPane from "./ChatRoomListPane";
import http from "../../../api/http";

vi.mock("../../../api/http", () => ({ default: { get: vi.fn() } }));

const direct = {
  roomId: 1, roomName: "김철수", memberCount: 2, unreadCount: 3,
  lastMessageContent: "안녕하세요", lasMessageTime: "2026-11-03T13:10:00",
  members: [{ userId: 2, name: "김철수", profileImageUrl: "" }],
};
const group = {
  roomId: 2, roomName: "개발팀", memberCount: 6, unreadCount: 0,
  lastMessageContent: "회의합시다", lasMessageTime: "2026-11-03T12:00:00",
  members: [1, 2, 3, 4].map(i => ({ userId: 10 + i, name: `사람${i}`, profileImageUrl: "" })),
};

const renderList = (rooms) =>
  render(
    <ChatRoomListPane
      tabIdx={0} setTabIdx={() => {}} roomList={rooms} selectedRoomId={null}
      setSelectedRoomId={() => {}} formatTime={() => "오후 01:10"} highlightedRoomId={null}
    />
  );

const itemOf = (name) => screen.getByText(name, { selector: "p, span, h6, div" }).closest("li");

describe("채팅방 목록", () => {
  it("1:1 방은 상대 사진 1개, 단체방은 참여자 사진 4개로 보여준다", () => {
    renderList([direct, group]);
    const directAvatar = within(itemOf("안녕하세요")).getByTestId("room-avatar");
    const groupAvatar = within(itemOf("회의합시다")).getByTestId("room-avatar");
    expect(directAvatar.dataset.kind).toBe("direct");
    expect(within(directAvatar).getAllByText(/김/)).toHaveLength(1);
    expect(groupAvatar.dataset.kind).toBe("group");
    expect(groupAvatar.children).toHaveLength(4);
  });

  it("\"1:1\"과 \"단체 채팅\" 표시는 없다", () => {
    renderList([direct, group]);
    expect(screen.queryByText("1:1")).toBeNull();
    expect(screen.queryByText("단체 채팅")).toBeNull();
  });

  it("마지막 메시지 시간은 오른쪽 칸에 있고 그 아래에 안 읽은 수가 있다", () => {
    renderList([direct]);
    const time = screen.getByTestId("room-last-time");
    expect(time.textContent).toBe("오후 01시 10분");
    const rightColumn = time.parentElement;
    expect(rightColumn.lastElementChild.textContent).toBe("3");
    expect(rightColumn.previousElementSibling.textContent).toContain("안녕하세요");
  });

  it("방마다 참여자 API를 따로 부르지 않는다", () => {
    renderList([direct, group]);
    expect(http.get).not.toHaveBeenCalled();
  });
});
