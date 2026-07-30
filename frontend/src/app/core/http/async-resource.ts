import { DestroyRef, Signal, WritableSignal, computed, inject, signal } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { humanizeError } from './http-status';

export type AsyncStatus = 'idle' | 'loading' | 'ready' | 'error';

/**
 * One request, every state it can be in, as signals.
 *
 * Screens used to hold a bare `signal<T[]>([])` and `subscribe(r => sig.set(r))`,
 * which collapses three different situations into one empty array: still
 * loading, loaded and genuinely empty, and failed. So every list rendered "No
 * appointments yet" while its request was in flight — asserting a fact the app
 * did not yet know — and rendered the same thing when the request failed, hiding
 * the failure entirely.
 *
 * The distinctions that earn their keep:
 *
 *  - `idle` vs `loading` — a screen whose request depends on a choice the user
 *    has not made yet ("pick a doctor, then a date") must show a prompt, not a
 *    skeleton for data it was never going to fetch.
 *  - `loading` vs `refreshing` — a search-as-you-type list already showing rows
 *    should not blank itself back to a skeleton on every keystroke. It keeps the
 *    rows and marks them stale.
 *
 * Not built on Angular's `rxResource`: it is still experimental in 19 and its
 * shape changed in 20. Fifty lines we own beats an API that moves underneath a
 * project meant to keep working.
 */
export class AsyncResource<T> {

  private readonly statusSignal = signal<AsyncStatus>('idle');
  private readonly valueSignal: WritableSignal<T | null>;
  private readonly errorSignal = signal<string | null>(null);
  private inFlight: Subscription | null = null;

  readonly status: Signal<AsyncStatus> = this.statusSignal.asReadonly();
  readonly error: Signal<string | null> = this.errorSignal.asReadonly();
  readonly value: Signal<T | null>;

  /** Nothing requested yet — show a prompt, not a spinner. */
  readonly idle: Signal<boolean>;
  /** Requested with nothing to show yet — render a skeleton. */
  readonly loading: Signal<boolean>;
  /** Re-fetching with previous data on screen — keep it, mark it stale. */
  readonly refreshing: Signal<boolean>;
  readonly failed: Signal<boolean>;

  constructor(private readonly source: () => Observable<T>, initial: T | null = null) {
    this.valueSignal = signal<T | null>(initial);
    this.value = this.valueSignal.asReadonly();

    this.idle = computed(() => this.statusSignal() === 'idle');
    this.loading = computed(() =>
      this.statusSignal() === 'loading' && this.valueSignal() === null);
    this.refreshing = computed(() =>
      this.statusSignal() === 'loading' && this.valueSignal() !== null);
    this.failed = computed(() => this.statusSignal() === 'error');

    // Needs an injection context, so construct this during component
    // construction (a field initializer is fine), never lazily in a handler.
    inject(DestroyRef).onDestroy(() => this.inFlight?.unsubscribe());
  }

  /**
   * (Re)runs the request. Any in-flight attempt is cancelled rather than raced,
   * so the newest call always wins — the switchMap guarantee, without making
   * every caller build a Subject to get it.
   */
  reload(): void {
    this.inFlight?.unsubscribe();
    this.statusSignal.set('loading');
    this.errorSignal.set(null);
    this.inFlight = this.source().subscribe({
      next: value => {
        this.valueSignal.set(value);
        this.statusSignal.set('ready');
      },
      error: err => {
        // Same wording the global interceptor uses, so an inline message and a
        // toast never disagree about what just happened.
        this.errorSignal.set(humanizeError(err));
        this.statusSignal.set('error');
      }
    });
  }

  /** Back to the un-asked state, e.g. the user cleared their selection. */
  reset(): void {
    this.inFlight?.unsubscribe();
    this.inFlight = null;
    this.valueSignal.set(null);
    this.errorSignal.set(null);
    this.statusSignal.set('idle');
  }

  /** Replaces the value without a round trip, for optimistic local updates. */
  set(value: T): void {
    this.valueSignal.set(value);
    this.statusSignal.set('ready');
    this.errorSignal.set(null);
  }
}

/**
 * Creates a resource and starts loading immediately. Call during construction —
 * it needs an injection context to tie its subscription to the component.
 *
 *   readonly appointments = asyncResource(() => this.service.mine());
 */
export function asyncResource<T>(source: () => Observable<T>,
                                 initial: T | null = null): AsyncResource<T> {
  const resource = new AsyncResource(source, initial);
  resource.reload();
  return resource;
}

/**
 * Same, but stays `idle` until something calls reload() — for requests that
 * depend on a choice the user has not made yet.
 */
export function deferredResource<T>(source: () => Observable<T>): AsyncResource<T> {
  return new AsyncResource(source, null);
}
