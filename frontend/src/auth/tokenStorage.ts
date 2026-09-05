import type { UserRole } from '../types/domain';

// Demo-only: tokens live in localStorage rather than an httpOnly cookie, matching this project's
// existing "dev tokens are visible in API responses / devOtp fields" posture — see CLAUDE.md's
// synthetic-data-only scope. A real deployment would use httpOnly, Secure, SameSite cookies.
const STORAGE_KEY = 'meridian.auth.session';

export interface StoredSession {
  accessToken: string;
  refreshToken: string;
  userId: string;
  email: string;
  role: UserRole;
}

export function loadSession(): StoredSession | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredSession;
  } catch {
    localStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

export function saveSession(session: StoredSession): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  localStorage.removeItem(STORAGE_KEY);
}
