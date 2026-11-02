import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup, configure } from "@testing-library/react";

// 기본 1초는 PC가 바쁠 때 MUI 화면이 뜨기 전에 끝나 코드가 멀쩡해도 실패함
configure({ asyncUtilTimeout: 10000 });

afterEach(() => {
  cleanup();
});
