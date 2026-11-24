import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  testMatch: '*.spec.mjs',
  timeout: 30000,
  workers: 1,
  use: {
    viewport: { width: 412, height: 820 },
    launchOptions: process.env.KIXYU_CHROME_PATH ? { executablePath: process.env.KIXYU_CHROME_PATH } : {},
  },
});
