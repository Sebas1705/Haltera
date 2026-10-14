import { defineConfig } from 'vitest/config';

// One emulator for every test file: run files one after another.
export default defineConfig({
  test: { fileParallelism: false, testTimeout: 20000, hookTimeout: 30000 },
});
