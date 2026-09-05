import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';

// Without `globals: true` in vite.config.ts, @testing-library/react can't auto-detect a global
// `afterEach` to register its own cleanup, so it must be wired up explicitly here — otherwise
// each render() leaks into the next test's DOM, causing spurious "multiple elements found" failures.
afterEach(() => {
  cleanup();
});
