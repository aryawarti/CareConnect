import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, finalize, map, of, shareReplay } from 'rxjs';
import { ApiEnvelope, AuthResponse, AuthUser } from './auth.models';

/**
 * Token strategy (docs/architecture/security.md):
 *  - access token: kept in memory only (a signal) — never persisted, gone on
 *    tab close, invisible to other scripts' storage scans.
 *  - refresh token: localStorage for v1 so a page reload can restore the
 *    session. Documented XSS trade-off; upgrade path is an HttpOnly cookie
 *    flow, which needs cookie handling at the gateway.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private static readonly REFRESH_KEY = 'cc.refreshToken';

  private readonly http = inject(HttpClient);

  private readonly accessTokenSignal = signal<string | null>(null);
  private readonly userSignal = signal<AuthUser | null>(null);

  /** Shared in-flight refresh; see refresh() for why this must be single-flight. */
  private refreshInFlight: Observable<AuthUser | null> | null = null;

  readonly user = computed(() => this.userSignal());
  readonly isAuthenticated = computed(() => this.accessTokenSignal() !== null);

  get accessToken(): string | null {
    return this.accessTokenSignal();
  }

  login(email: string, password: string): Observable<AuthUser> {
    return this.http.post<ApiEnvelope<AuthResponse>>('/api/auth/login', { email, password })
      .pipe(map(r => this.storeSession(r.data)));
  }

  /** `role` is PATIENT or DOCTOR only — the server rejects anything else. */
  register(email: string, password: string, role: 'PATIENT' | 'DOCTOR' = 'PATIENT'):
      Observable<AuthUser> {
    return this.http.post<ApiEnvelope<AuthResponse>>('/api/auth/register',
        { email, password, role })
      .pipe(map(r => this.storeSession(r.data)));
  }

  /**
   * Called on app start, by the auth guard, and by the interceptor after a 401.
   *
   * SINGLE-FLIGHT, and that is a correctness requirement, not an optimization:
   * the backend rotates refresh tokens and treats a *replayed* (already
   * consumed) token as theft by revoking every session. Two concurrent 401s
   * firing two refreshes with the same token would therefore log the user out
   * — which is exactly the bug this replaced. Concurrent callers now share one
   * in-flight request; late subscribers get its result via shareReplay.
   */
  refresh(): Observable<AuthUser | null> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }
    const refreshToken = localStorage.getItem(AuthService.REFRESH_KEY);
    if (!refreshToken) {
      return of(null);
    }
    this.refreshInFlight = this.http
      .post<ApiEnvelope<AuthResponse>>('/api/auth/refresh', { refreshToken })
      .pipe(
        map(r => this.storeSession(r.data)),
        // A failed refresh must still *resolve* (to null), never error out — the
        // auth guard maps this result to a redirect, so an errored observable
        // would leave the router hanging on a blank page. Only a 401 means the
        // token is genuinely dead (drop it); a 5xx/0 is a backend still warming
        // up, so we keep the token and let the next attempt succeed.
        catchError((err: { status?: number }) => {
          if (err?.status === 401) {
            this.clearSession();
          }
          return of(null);
        }),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    return this.refreshInFlight;
  }

  logout(): Observable<void> {
    const refreshToken = localStorage.getItem(AuthService.REFRESH_KEY);
    this.clearSession();
    if (!refreshToken) {
      return of(void 0);
    }
    // Best-effort server-side revocation; local state is already cleared.
    return this.http.post('/api/auth/logout', { refreshToken }).pipe(map(() => void 0));
  }

  clearSession(): void {
    this.accessTokenSignal.set(null);
    this.userSignal.set(null);
    this.refreshInFlight = null;
    localStorage.removeItem(AuthService.REFRESH_KEY);
  }

  private storeSession(auth: AuthResponse): AuthUser {
    this.accessTokenSignal.set(auth.accessToken);
    localStorage.setItem(AuthService.REFRESH_KEY, auth.refreshToken);
    const user: AuthUser = { userId: auth.userId, email: auth.email, roles: auth.roles };
    this.userSignal.set(user);
    return user;
  }
}
