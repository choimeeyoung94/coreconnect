import { expect, test } from '@playwright/test'

// 백엔드 없이 화면이 뜨는지만 확인. 로그인 뒤 흐름은 시드 데이터가 준비되면 추가
test('로그인 화면이 뜬다', async ({ page }) => {
  await page.goto('/login')

  await expect(page.getByRole('heading', { name: '코어커넥트' })).toBeVisible()
  await expect(page.locator('form')).toBeVisible()
})
