import { defineConfig } from '@playwright/test'

// 브라우저를 내려받지 않고 PC에 설치된 Chrome을 화면 없이 사용
// 개발 서버는 첫 화면에서 파일 수백 개를 하나씩 변환해 느리므로, 빌드 결과를 띄워서 검사
export default defineConfig({
  testDir: './e2e',
  outputDir: './e2e/test-results',
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:4173',
    channel: 'chrome',
    headless: true,
  },
  webServer: {
    command: 'npm run build && npm run preview -- --port 4173 --strictPort',
    url: 'http://localhost:4173',
    reuseExistingServer: false,
    timeout: 300000,
  },
})
