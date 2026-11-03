import { useRef } from "react";
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import ChatMessageInputBox from "./ChatMessageInputBox";

// ChatLayout.handleSend처럼 ref로 값을 읽고, 보낸 뒤 ref로 지우는 부모
function Parent({ onSent = () => {}, socketConnected = true, onOpacity = () => {} }) {
  const inputRef = useRef();
  return (
    <ChatMessageInputBox
      inputRef={inputRef}
      onSend={async () => {
        onSent(inputRef.current.value);
        inputRef.current.value = "";
      }}
      onFileUpload={() => {}}
      socketConnected={socketConnected}
      messageOpacity={1}
      onMessageOpacityChange={onOpacity}
    />
  );
}

const box = () => screen.getByPlaceholderText("메시지 입력");
const sendButton = () => screen.getByRole("button", { name: "전송" });
const type = (value) => fireEvent.change(box(), { target: { value } });

describe("채팅 입력창", () => {
  it("글자를 입력해야 전송 버튼이 켜지고, 공백만 있으면 꺼져 있다", () => {
    render(<Parent />);
    expect(sendButton().disabled).toBe(true);
    type("   ");
    expect(sendButton().disabled).toBe(true);
    type("안녕");
    expect(sendButton().disabled).toBe(false);
  });

  it("연결이 끊겼으면 글자가 있어도 전송 버튼이 꺼져 있다", () => {
    render(<Parent socketConnected={false} />);
    type("안녕");
    expect(sendButton().disabled).toBe(true);
  });

  it("전송 버튼으로 보내면 입력칸이 비고 버튼이 다시 꺼진다", async () => {
    const onSent = vi.fn();
    render(<Parent onSent={onSent} />);
    type("안녕");
    fireEvent.click(sendButton());
    await waitFor(() => expect(sendButton().disabled).toBe(true));
    expect(onSent).toHaveBeenCalledWith("안녕");
    expect(box().value).toBe("");
  });

  it("Enter는 보내고, Shift+Enter와 한글 조합 중 Enter는 보내지 않는다", async () => {
    const onSent = vi.fn();
    render(<Parent onSent={onSent} />);
    type("안녕");
    fireEvent.keyDown(box(), { key: "Enter", shiftKey: true });
    fireEvent.keyDown(box(), { key: "Enter", isComposing: true });
    expect(onSent).not.toHaveBeenCalled();
    fireEvent.keyDown(box(), { key: "Enter" });
    await waitFor(() => expect(onSent).toHaveBeenCalledTimes(1));
  });

  it("이모티콘 10개 중 고른 것이 들어가고 전송 버튼이 켜진다", () => {
    render(<Parent />);
    fireEvent.click(screen.getByRole("button", { name: "이모티콘" }));
    const emojiButtons = screen.getAllByRole("button").filter((b) => b.closest(".MuiPopover-paper"));
    expect(emojiButtons).toHaveLength(10);
    fireEvent.click(screen.getByRole("button", { name: "👍" }));
    expect(box().value).toBe("👍");
    expect(sendButton().disabled).toBe(false);
  });

  it("이모티콘은 글자 사이 커서 위치에 들어간다", () => {
    render(<Parent />);
    type("가나");
    box().setSelectionRange(1, 1);
    fireEvent.click(screen.getByRole("button", { name: "이모티콘" }));
    fireEvent.click(screen.getByRole("button", { name: "🎉" }));
    expect(box().value).toBe("가🎉나");
  });

  it("슬라이더를 움직이면 진하기 값을 부모에게 알린다", () => {
    const onOpacity = vi.fn();
    render(<Parent onOpacity={onOpacity} />);
    fireEvent.change(screen.getByRole("slider", { name: "대화 내용 진하기" }), { target: { value: 0.5 } });
    expect(onOpacity).toHaveBeenCalledWith(0.5);
  });
});
