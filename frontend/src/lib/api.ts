import { clearSession, getAccessToken } from '@/stores/useAuthStore';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export async function fetchApi<T>(endpoint: string, options?: RequestInit): Promise<T> {
  const token = getAccessToken();

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options?.headers || {}),
    },
  });

  // 토큰이 만료되었거나 유효하지 않으면 세션을 비우고 로그인 화면으로 보냅니다.
  if (response.status === 401) {
    clearSession();
    if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
      window.location.href = '/login';
    }
    throw new ApiError('로그인이 필요합니다.', 401);
  }

  const json = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new ApiError(json.message || '요청을 처리하지 못했습니다.', response.status);
  }

  // ApiResponse<T>에서 data 필드만 꺼내서 반환
  return json.data as T;
}
