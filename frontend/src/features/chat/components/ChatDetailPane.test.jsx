import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import ChatDetailPane from "./ChatDetailPane";

vi.mock("../../../App", async () => {
  const { createContext } = await import("react");
  return { UserProfileContext: createContext({ userProfile: { id: 1, email: "me@test.com" } }) };
});
vi.mock("../api/ChatRoomApi", () => ({
  fetchChatRoomUsers: vi.fn().mockResolvedValue([]),
  leaveChatRoom: vi.fn(),
}));
vi.mock("./ChatRoomParticipantsDialog", () => ({ default: () => null }));
vi.mock("./ChatRoomInviteDialog", () => ({ default: () => null }));

const renderPane = (messages = [{ id: 1, messageContent: "메시지", senderId: 2 }], roomType = "group") =>
  render(
    <ChatDetailPane
      selectedRoom={{ roomId: 1, roomName: "방", roomType }}
      messages={messages}
      inputRef={{ current: null }}
      onSend={() => {}}
      onFileUpload={() => {}}
      socketConnected
    />
  );

const opacity = () => getComputedStyle(screen.getByTestId("chat-message-content")).opacity;
const slider = () => screen.getByRole("slider", { name: "대화 내용 진하기" });

describe("대화 내용 진하기", () => {
  beforeEach(() => localStorage.clear());

  it("슬라이더를 움직이면 대화 내용 투명도가 바뀌고 다음에 열어도 유지된다", () => {
    const { unmount } = renderPane();
    expect(opacity()).toBe("1");
    fireEvent.change(slider(), { target: { value: 0.5 } });
    expect(opacity()).toBe("0.5");
    unmount();
    renderPane();
    expect(opacity()).toBe("0.5");
  });

  it("저장된 값이 범위를 벗어나면 기본값으로 보여준다", () => {
    localStorage.setItem("chat.messageOpacity", "0");
    renderPane();
    expect(opacity()).toBe("1");
  });
});

describe("보낸 사람 표시", () => {
  it("이름/소속팀/직급 순서로 보여준다", () => {
    renderPane([{ id: 1, messageContent: "안녕", senderId: 2, senderName: "김철수", senderDeptName: "개발팀", senderJobGrade: "ASSISTANT_MANAGER" }]);
    expect(screen.getByText("김철수/개발팀/대리")).toBeTruthy();
  });

  it("부서와 직급이 비어 있으면 있는 값만 보여준다", () => {
    renderPane([{ id: 1, messageContent: "안녕", senderId: 2, senderName: "알 수 없음", senderDeptName: "", senderJobGrade: null }]);
    expect(screen.getByText("알 수 없음")).toBeTruthy();
  });

  it("1:1 방의 상대방 메시지에도 보여준다", () => {
    renderPane([{ id: 1, messageContent: "안녕", senderId: 2, senderName: "김철수", senderDeptName: "개발팀", senderJobGrade: "MANAGER" }], "alone");
    expect(screen.getByText("김철수/개발팀/과장")).toBeTruthy();
  });

  it("내 메시지에는 표시하지 않는다", () => {
    renderPane([{ id: 1, messageContent: "안녕", senderId: 1, senderName: "나", senderDeptName: "개발팀", senderJobGrade: "STAFF" }]);
    expect(screen.queryByText("나/개발팀/사원")).toBeNull();
  });
});
