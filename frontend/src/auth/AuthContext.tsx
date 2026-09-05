import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { apiClient, setSessionExpiredHandler } from '../api/client';
import type { LoginResponse, TokenResponse, UserRole } from '../types/domain';
import { clearSession, loadSession, saveSession, type StoredSession } from './tokenStorage';

export interface AuthUser {
  userId: string;
  email: string;
  role: UserRole;
}

interface MfaChallenge {
  mfaChallengeId: string;
  devOtp?: string;
}

interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isCustomer: boolean;
  login: (email: string, password: string) => Promise<{ mfaRequired: boolean; challenge?: MfaChallenge; user?: AuthUser }>;
  verifyMfa: (challengeId: string, otp: string) => Promise<AuthUser>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function toUser(session: StoredSession): AuthUser {
  return { userId: session.userId, email: session.email, role: session.role };
}

function storeTokens(tokens: TokenResponse): AuthUser {
  const session: StoredSession = {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    userId: tokens.userId,
    email: tokens.email,
    role: tokens.role,
  };
  saveSession(session);
  return toUser(session);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => {
    const session = loadSession();
    return session ? toUser(session) : null;
  });

  useEffect(() => {
    setSessionExpiredHandler(() => setUser(null));
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isCustomer: user?.role === 'CUSTOMER',

      async login(email, password) {
        const { data } = await apiClient.post<LoginResponse>('/api/v1/auth/login', { email, password });
        if (data.mfaRequired && data.mfaChallengeId) {
          return { mfaRequired: true, challenge: { mfaChallengeId: data.mfaChallengeId, devOtp: data.devOtp } };
        }
        if (data.tokens) {
          const authUser = storeTokens(data.tokens);
          setUser(authUser);
          return { mfaRequired: false, user: authUser };
        }
        return { mfaRequired: false };
      },

      async verifyMfa(challengeId, otp) {
        const { data } = await apiClient.post<TokenResponse>('/api/v1/auth/mfa/verify', {
          mfaChallengeId: challengeId,
          otp,
        });
        const authUser = storeTokens(data);
        setUser(authUser);
        return authUser;
      },

      async logout() {
        const session = loadSession();
        if (session) {
          try {
            await apiClient.post('/api/v1/auth/logout', { refreshToken: session.refreshToken });
          } catch {
            // best-effort — the local session is cleared regardless
          }
        }
        clearSession();
        setUser(null);
      },
    }),
    [user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider');
  return context;
}
