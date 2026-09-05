import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import MockAdapter from 'axios-mock-adapter';
import { apiClient } from '../../api/client';
import { AuthProvider, useAuth } from '../AuthContext';
import { loadSession } from '../tokenStorage';

let mock: MockAdapter;

beforeEach(() => {
  mock = new MockAdapter(apiClient);
  localStorage.clear();
});

afterEach(() => {
  mock.restore();
});

/** A tiny consumer so the hook's behavior can be exercised through real user interaction. */
function Probe() {
  const { user, isAuthenticated, login, verifyMfa, logout } = useAuth();
  return (
    <div>
      <div data-testid="state">{isAuthenticated ? `in:${user?.email}:${user?.role}` : 'out'}</div>
      <button onClick={() => login('cust1@example.com', 'Correct-Horse1!')}>login</button>
      <button onClick={() => verifyMfa('challenge-1', '123456')}>verify</button>
      <button onClick={() => logout()}>logout</button>
    </div>
  );
}

function renderProbe() {
  return render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  );
}

describe('AuthContext', () => {
  it('starts unauthenticated with no stored session', () => {
    renderProbe();
    expect(screen.getByTestId('state')).toHaveTextContent('out');
  });

  it('logs in directly and stores the session when MFA is not required', async () => {
    mock.onPost('/api/v1/auth/login').reply(200, {
      mfaRequired: false,
      tokens: {
        accessToken: 'access-1',
        refreshToken: 'refresh-1',
        tokenType: 'Bearer',
        expiresIn: 900,
        userId: 'u1',
        email: 'cust1@example.com',
        role: 'CUSTOMER',
      },
    });
    renderProbe();

    await act(() => userEvent.click(screen.getByText('login')));

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('in:cust1@example.com:CUSTOMER'));
    expect(loadSession()?.accessToken).toBe('access-1');
  });

  it('does not store a session when MFA is required — waits for verifyMfa', async () => {
    mock.onPost('/api/v1/auth/login').reply(200, {
      mfaRequired: true,
      mfaChallengeId: 'challenge-1',
      expiresInSeconds: 300,
      devOtp: '999999',
    });
    mock.onPost('/api/v1/auth/mfa/verify').reply(200, {
      accessToken: 'access-2',
      refreshToken: 'refresh-2',
      tokenType: 'Bearer',
      expiresIn: 900,
      userId: 'u1',
      email: 'cust1@example.com',
      role: 'CUSTOMER',
    });
    renderProbe();

    await act(() => userEvent.click(screen.getByText('login')));
    expect(screen.getByTestId('state')).toHaveTextContent('out');
    expect(loadSession()).toBeNull();

    await act(() => userEvent.click(screen.getByText('verify')));
    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('in:cust1@example.com:CUSTOMER'));
    expect(loadSession()?.accessToken).toBe('access-2');
  });

  it('clears the session on logout even if the server logout call fails', async () => {
    mock.onPost('/api/v1/auth/login').reply(200, {
      mfaRequired: false,
      tokens: {
        accessToken: 'access-1',
        refreshToken: 'refresh-1',
        tokenType: 'Bearer',
        expiresIn: 900,
        userId: 'u1',
        email: 'cust1@example.com',
        role: 'CUSTOMER',
      },
    });
    mock.onPost('/api/v1/auth/logout').reply(500);
    renderProbe();
    await act(() => userEvent.click(screen.getByText('login')));
    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('in:'));

    await act(() => userEvent.click(screen.getByText('logout')));

    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('out'));
    expect(loadSession()).toBeNull();
  });
});
