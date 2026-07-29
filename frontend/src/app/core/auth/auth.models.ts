export interface AuthUser {
  userId: string;
  email: string;
  roles: string[];
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  userId: string;
  email: string;
  roles: string[];
}

/** Standard success envelope (docs/api/guidelines.md). */
export interface ApiEnvelope<T> {
  data: T;
}
