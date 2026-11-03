import React, { useState } from "react";
import { Box, TextField, IconButton, Button, Popover, Slider, Tooltip } from "@mui/material";
import AttachFileIcon from "@mui/icons-material/AttachFile";
import SentimentSatisfiedAltIcon from "@mui/icons-material/SentimentSatisfiedAlt";
import FilePreviewBox from "./FilePreviewBox";

const EMOJIS = ["😀", "😂", "😍", "😢", "😡", "👍", "🙏", "🎉", "❤️", "🔥"];

// 채팅 입력창 + 파일업로드 + 이모티콘 + 대화 내용 진하기
function ChatMessageInputBox({ inputRef, onSend, onFileUpload, socketConnected, messageOpacity, onMessageOpacityChange }) {
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [hasText, setHasText] = useState(false);
  const [emojiAnchorEl, setEmojiAnchorEl] = useState(null);

  // 입력값은 화면(ChatLayout)이 ref로 직접 읽고 지우므로, 버튼 상태는 실제 입력칸 값을 보고 정한다
  const syncHasText = () => setHasText(Boolean(inputRef.current?.value.trim()));

  const handleSend = async () => {
    await onSend();
    syncHasText();
  };

  const handleEmojiSelect = (emoji) => {
    const input = inputRef.current;
    const start = input.selectionStart;
    input.value = input.value.slice(0, start) + emoji + input.value.slice(input.selectionEnd);
    input.selectionStart = input.selectionEnd = start + emoji.length;
    setEmojiAnchorEl(null);
    syncHasText();
    input.focus();
  };

  const handleFileSelect = (e) => {
    const files = Array.from(e.target.files || []);
    if (files.length === 0) return;

    // 50MB 제한 체크
    const MAX_SIZE = 50 * 1024 * 1024; // 50MB
    const totalSize = files.reduce((sum, file) => sum + file.size, 0);
    
    if (totalSize > MAX_SIZE) {
      alert(`총 파일 크기가 50MB를 초과합니다. (현재: ${(totalSize / 1024 / 1024).toFixed(2)}MB)`);
      e.target.value = "";
      return;
    }

    // 개별 파일 크기 체크
    const oversizedFiles = files.filter(file => file.size > MAX_SIZE);
    if (oversizedFiles.length > 0) {
      alert(`일부 파일이 50MB를 초과합니다: ${oversizedFiles.map(f => f.name).join(", ")}`);
      e.target.value = "";
      return;
    }

    setSelectedFiles((prev) => [...prev, ...files]);
    e.target.value = "";
  };

  const handleRemoveFile = (index) => {
    setSelectedFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const handleUpload = () => {
    if (selectedFiles.length > 0) {
      onFileUpload(selectedFiles);
      setSelectedFiles([]);
    }
  };

  return (
    <Box sx={{
      width: "100%",
      display: "flex",
      flexDirection: "column",
      background: "#f8fbfd"
    }}>
      {/* 파일 미리보기 */}
      {selectedFiles.length > 0 && (
        <FilePreviewBox files={selectedFiles} onRemove={handleRemoveFile} />
      )}
      
      {/* 입력 영역 */}
      <Box sx={{
        display: "flex",
        flexDirection: "column",
        px: 2, pt: 1.5, pb: 1,
        borderTop: "1px solid #e3e8ef",
        background: "#fff",
      }}>
        <TextField
          inputRef={inputRef}
          variant="standard"
          placeholder="메시지 입력"
          multiline
          minRows={2}
          maxRows={5}
          fullWidth
          slotProps={{ input: { disableUnderline: true } }}
          onChange={syncHasText}
          onKeyDown={e => {
            // 한글 조합 중 Enter는 글자 확정이므로 보내지 않는다
            if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault();
              handleSend();
            }
          }}
        />
        <Box sx={{ display: "flex", alignItems: "center", gap: 0.5, mt: 0.5 }}>
          <IconButton size="small" aria-label="이모티콘" onClick={e => setEmojiAnchorEl(e.currentTarget)}>
            <SentimentSatisfiedAltIcon />
          </IconButton>
          <label htmlFor="chat-file-upload" style={{ display: "inline-flex" }}>
            <input
              type="file"
              multiple
              style={{ display: "none" }}
              id="chat-file-upload"
              // accept 속성 제거: 모든 파일 타입 허용 (PDF, 이미지, 문서, 압축파일 등 모든 파일 업로드 가능)
              onChange={handleFileSelect}
            />
            <IconButton size="small" component="span" aria-label="파일 첨부">
              <AttachFileIcon />
            </IconButton>
          </label>
          {selectedFiles.length > 0 && (
            <Button
              variant="outlined"
              color="primary"
              size="small"
              onClick={handleUpload}
              disabled={!socketConnected}
            >
              업로드 ({selectedFiles.length})
            </Button>
          )}
          <Tooltip title="대화 내용 진하기">
            <Slider
              size="small"
              aria-label="대화 내용 진하기"
              min={0.2}
              max={1}
              step={0.1}
              value={messageOpacity}
              onChange={(_, value) => onMessageOpacityChange(value)}
              sx={{ width: 80, ml: "auto", mr: 1.5 }}
            />
          </Tooltip>
          <Button
            variant="contained"
            size="small"
            onClick={handleSend}
            disabled={!hasText || !socketConnected}
          >
            전송
          </Button>
        </Box>
      </Box>
      <Popover
        open={Boolean(emojiAnchorEl)}
        anchorEl={emojiAnchorEl}
        onClose={() => setEmojiAnchorEl(null)}
        anchorOrigin={{ vertical: "top", horizontal: "left" }}
        transformOrigin={{ vertical: "bottom", horizontal: "left" }}
      >
        <Box sx={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", p: 0.5 }}>
          {EMOJIS.map(emoji => (
            <IconButton key={emoji} size="small" onClick={() => handleEmojiSelect(emoji)}>
              {emoji}
            </IconButton>
          ))}
        </Box>
      </Popover>
    </Box>
  );
}
export default ChatMessageInputBox;