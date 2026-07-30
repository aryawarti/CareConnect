import { Injector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject, throwError } from 'rxjs';
import { AsyncResource, asyncResource, deferredResource } from './async-resource';

/**
 * The state machine every screen now depends on. If `loading` and "empty" ever
 * collapse back into each other, the whole point of this class is lost — and
 * that is exactly the bug it was written to kill, so it gets the tests.
 */
describe('AsyncResource', () => {

  let injector: Injector;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    injector = TestBed.inject(Injector);
  });

  /** The class needs an injection context (it ties cleanup to DestroyRef). */
  function make<T>(factory: () => AsyncResource<T>): AsyncResource<T> {
    return runInInjectionContext(injector, factory);
  }

  it('starts loading with nothing to show', () => {
    const source = new Subject<string[]>();
    const resource = make(() => asyncResource(() => source));

    expect(resource.loading()).toBeTrue();
    expect(resource.refreshing()).toBeFalse();
    expect(resource.value()).toBeNull();
  });

  it('exposes the value once it arrives', () => {
    const source = new Subject<string[]>();
    const resource = make(() => asyncResource(() => source));

    source.next(['a', 'b']);

    expect(resource.loading()).toBeFalse();
    expect(resource.value()).toEqual(['a', 'b']);
    expect(resource.status()).toBe('ready');
  });

  /**
   * The distinction the whole class exists for: an empty result is "ready", not
   * "loading". Screens branch on these, and conflating them is what made lists
   * claim "nothing here" before they knew.
   */
  it('treats an empty result as ready, not as still loading', () => {
    const source = new Subject<string[]>();
    const resource = make(() => asyncResource(() => source));

    source.next([]);

    expect(resource.loading()).toBeFalse();
    expect(resource.failed()).toBeFalse();
    expect(resource.value()).toEqual([]);
  });

  it('reports failure instead of looking empty', () => {
    const resource = make(() =>
      asyncResource<string[]>(() => throwError(() => ({ status: 500 }))));

    expect(resource.failed()).toBeTrue();
    expect(resource.loading()).toBeFalse();
    expect(resource.error()).toBeTruthy();
    expect(resource.value()).withContext('no data should be invented').toBeNull();
  });

  /**
   * A reload with data already on screen is `refreshing`, not `loading`, so a
   * search-as-you-type list keeps its rows instead of flashing to a skeleton.
   */
  it('distinguishes refreshing from loading once data exists', () => {
    let current = new Subject<string[]>();
    const resource = make(() => asyncResource(() => current));
    current.next(['first']);

    current = new Subject<string[]>();
    resource.reload();

    expect(resource.loading()).withContext('data is on screen').toBeFalse();
    expect(resource.refreshing()).toBeTrue();
    expect(resource.value()).withContext('previous rows are kept').toEqual(['first']);
  });

  /** Newest wins: a slow earlier response must not overwrite a newer one. */
  it('cancels an in-flight request when reloaded', () => {
    const first = new Subject<string[]>();
    const second = new Subject<string[]>();
    let next = first;
    const resource = make(() => asyncResource(() => next));

    next = second;
    resource.reload();
    first.next(['stale']);
    second.next(['fresh']);

    expect(resource.value()).toEqual(['fresh']);
  });

  it('clears a previous error when reloaded', () => {
    let fail = true;
    const ok = new Subject<string[]>();
    const resource = make(() =>
      asyncResource<string[]>(() => (fail ? throwError(() => ({ status: 500 })) : ok)));
    expect(resource.failed()).toBeTrue();

    fail = false;
    resource.reload();
    ok.next(['recovered']);

    expect(resource.failed()).toBeFalse();
    expect(resource.error()).toBeNull();
    expect(resource.value()).toEqual(['recovered']);
  });

  describe('deferredResource', () => {

    it('is idle until asked, so screens can prompt instead of spinning', () => {
      const resource = make(() => deferredResource(() => new Subject<string[]>()));

      expect(resource.idle()).toBeTrue();
      expect(resource.loading())
        .withContext('a request that was never made is not loading')
        .toBeFalse();
    });

    it('leaves idle on reload and can be reset back to it', () => {
      const source = new Subject<string[]>();
      const resource = make(() => deferredResource(() => source));

      resource.reload();
      expect(resource.idle()).toBeFalse();
      source.next(['x']);

      resource.reset();
      expect(resource.idle()).toBeTrue();
      expect(resource.value()).toBeNull();
    });
  });
});
