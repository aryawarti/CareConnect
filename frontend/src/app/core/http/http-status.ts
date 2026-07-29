import { HttpErrorResponse } from '@angular/common/http';

/**
 * Turns any HTTP failure into a clear, human sentence a non-technical user can
 * act on. Our services return RFC 7807 problem details ({ detail, title }); we
 * prefer that message when present, and otherwise map the status code to plain
 * language — so a raw 503 becomes "the service is still starting up" instead of
 * a silent failure or a stack trace.
 */
export function humanizeError(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  const e = err as HttpErrorResponse | undefined;
  const status = e?.status ?? -1;
  const detail: string | undefined =
    (e?.error && typeof e.error === 'object' ? (e.error as { detail?: string }).detail : undefined);

  if (status === 0) {
    return 'Can’t reach the server. Make sure the backend is running, then try again.';
  }
  if (status === 503) {
    return 'The service is still starting up. Please wait a few seconds and try again.';
  }
  if (status === 502 || status === 504) {
    return 'The server is temporarily unavailable. Please try again in a moment.';
  }
  if (status >= 500) {
    return detail ?? 'Something went wrong on our side. Please try again.';
  }
  if (status === 401) {
    return detail ?? 'Your session has expired, or the email and password don’t match.';
  }
  if (status === 403) {
    return detail ?? 'You don’t have permission to do that.';
  }
  if (status === 404) {
    return detail ?? 'We couldn’t find what you were looking for.';
  }
  if (status === 409) {
    return detail ?? 'That conflicts with the current state. Please refresh and try again.';
  }
  return detail ?? fallback;
}

/** True for problems that are the platform's fault, not the user's input. */
export function isSystemError(err: unknown): boolean {
  const status = (err as HttpErrorResponse | undefined)?.status ?? -1;
  return status === 0 || status >= 500;
}
