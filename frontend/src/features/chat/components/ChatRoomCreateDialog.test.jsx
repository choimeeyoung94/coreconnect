import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import ChatRoomCreateDialog from "./ChatRoomCreateDialog";

const showSnack = vi.fn();
vi.mock("../../../components/utils/SnackbarContext", () => ({ useSnackbarContext: () => ({ showSnack }) }));
vi.mock("../../../App", async () => {
  const { createContext } = await import("react");
  return { UserProfileContext: createContext({ userProfile: { id: 1, name: "관리자", email: "admin@test.com" } }) };
});
vi.mock("../../../api/http", () => ({
  default: {
    get: vi.fn().mockResolvedValue({ data: { data: [
      { userId: 1, name: "관리자", email: "admin@test.com" },
      { userId: 2, name: "김철수", email: "kim@test.com" },
    ] } }),
  },
}));

const openDialog = (onCreate) => {
  render(<ChatRoomCreateDialog open onClose={() => {}} onCreate={onCreate} />);
  fireEvent.change(screen.getByLabelText("채팅방 이름"), { target: { value: "방" } });
  fireEvent.click(screen.getByLabelText("1:1"));
};

describe("1:1 방 만들기", () => {
  beforeEach(() => showSnack.mockClear());

  it("상대를 고르지 않고 나만 있으면 만들지 않는다", async () => {
    const onCreate = vi.fn();
    openDialog(onCreate);
    await screen.findByText(/김철수/);
    fireEvent.click(screen.getByRole("button", { name: "생성" }));
    expect(onCreate).not.toHaveBeenCalled();
    expect(showSnack).toHaveBeenCalledWith("1:1 채팅은 본인 포함 2명을 선택해야 합니다.", "warning");
  });

  it("상대를 한 명 고르면 나와 상대 2명으로 만든다", async () => {
    const onCreate = vi.fn();
    openDialog(onCreate);
    fireEvent.click(await screen.findByText(/김철수/));
    fireEvent.click(screen.getByRole("button", { name: "생성" }));
    await waitFor(() => expect(onCreate).toHaveBeenCalled());
    expect(onCreate.mock.calls[0][0].userIds).toEqual([1, 2]);
  });
});
