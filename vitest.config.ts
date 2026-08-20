import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    // 单元测试环境为 node：RTC 契约测试通过 vi.mock 隔离 livekit-client，
    // 需要 MediaStream 的用例使用摘要中的轻量 stub，不引入 jsdom。
    environment: 'node',
    include: ['src/**/__tests__/**/*.test.ts'],
    reporters: 'default'
  }
})
