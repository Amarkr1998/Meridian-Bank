import { beforeEach, describe, expect, it } from 'vitest';
import { clearSession, loadSession, saveSession, type StoredSession } from '../tokenStorage';

const SESSION: StoredSession = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  userId: '11a3067d-154a-4230-93d6-0476501844eb',
  email: 'cust1@example.com',
  role: 'CUSTOMER',
};

describe('tokenStorage', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('returns null when nothing has been saved', () => {
    expect(loadSession()).toBeNull();
  });

  it('round-trips a saved session', () => {
    saveSession(SESSION);
    expect(loadSession()).toEqual(SESSION);
  });

  it('clears the session', () => {
    saveSession(SESSION);
    clearSession();
    expect(loadSession()).toBeNull();
  });

  it('treats corrupt stored JSON as no session, and removes it', () => {
    localStorage.setItem('meridian.auth.session', '{not valid json');
    expect(loadSession()).toBeNull();
    expect(localStorage.getItem('meridian.auth.session')).toBeNull();
  });
});
