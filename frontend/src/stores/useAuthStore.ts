import { create } from 'zustand';

const TOKEN_KEY = 'tokit_access_token';
const USER_KEY = 'tokit_auth_user';

export interface AuthUser {
  userId: number;
  email: string;
  name: string;
}

interface AuthState {
  token: string | null;
  user: AuthUser | null;
  /** localStorage 복원이 끝나기 전까지 true. 인증 가드가 성급히 리다이렉트하지 않도록 사용합니다. */
  loading: boolean;

  restore: () => void;
  signIn: (token: string, user: AuthUser) => void;
  signOut: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  user: null,
  loading: true,

  restore: () => {
    if (typeof window === 'undefined') return;
    try {
      const token = localStorage.getItem(TOKEN_KEY);
      const rawUser = localStorage.getItem(USER_KEY);
      set({
        token,
        user: rawUser ? (JSON.parse(rawUser) as AuthUser) : null,
        loading: false,
      });
    } catch {
      set({ token: null, user: null, loading: false });
    }
  },

  signIn: (token, user) => {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    set({ token, user, loading: false });
  },

  signOut: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    set({ token: null, user: null, loading: false });
  },
}));

/** fetchApi처럼 React 훅을 쓸 수 없는 곳에서 토큰을 읽기 위한 접근자입니다. */
export function getAccessToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function clearSession() {
  if (typeof window === 'undefined') return;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  useAuthStore.setState({ token: null, user: null, loading: false });
}
