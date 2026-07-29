import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { QueueService, QueueSnapshot } from '../../core/queue/queue.service';

/**
 * The waiting-room display board — designed to be thrown on a lobby TV.
 *
 * No login (kiosks don't have one), no clinical data (token numbers and first
 * names only), high contrast, huge type, and it updates itself over SSE. This
 * is the screen that makes the whole architecture legible to a non-technical
 * observer: a doctor clicks "call next" and the wall changes instantly.
 */
@Component({
  selector: 'cc-waiting-board',
  standalone: true,
  template: `
    <div class="board">
      <header>
        <div>
          <div class="brand">CareConnect</div>
          <div class="doctor">{{ snapshot()?.doctorName || 'Consultation room' }}</div>
        </div>
        <div style="text-align:right">
          <div class="clock">{{ now() }}</div>
          <div class="waiting-count">{{ snapshot()?.waiting ?? 0 }} waiting</div>
        </div>
      </header>

      <section class="serving">
        <div class="label">NOW SERVING</div>
        @if (spotlight(); as current) {
          <div class="token-huge" [class.blink]="current.status === 'CALLED'">
            {{ current.tokenNumber }}
          </div>
          <div class="name">
            {{ firstName(current.patientName) }}
            @if (current.status === 'CALLED') { — please proceed }
          </div>
        } @else {
          <div class="token-huge dim">—</div>
          <div class="name">Waiting for the next patient</div>
        }
      </section>

      <section class="upcoming">
        <div class="label">NEXT IN LINE</div>
        <div class="chips">
          @for (entry of upcoming(); track entry.id) {
            <div class="chip" [class.urgent]="entry.priority !== 'NORMAL'">
              <div class="chip-token">{{ entry.tokenNumber }}</div>
              <div class="chip-name">{{ firstName(entry.patientName) }}</div>
              @if (entry.estimatedWaitMinutes !== null) {
                <div class="chip-eta">~{{ entry.estimatedWaitMinutes }} min</div>
              }
            </div>
          } @empty {
            <div class="none">No one else is waiting</div>
          }
        </div>
      </section>

      <footer>
        Estimated waits are calculated from this doctor's recent consultation times
        and update automatically.
      </footer>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .board {
      min-height: 100vh; background: #08201e; color: #eafaf7;
      padding: 34px 44px; font-family: Roboto, system-ui, sans-serif;
      display: flex; flex-direction: column; gap: 26px;
    }
    header { display: flex; justify-content: space-between; align-items: flex-start; }
    .brand { font-size: 26px; font-weight: 700; color: #5eead4; letter-spacing: -.02em; }
    .doctor { font-size: 20px; opacity: .82; margin-top: 4px; }
    .clock { font-size: 28px; font-weight: 600; font-variant-numeric: tabular-nums; }
    .waiting-count { opacity: .7; margin-top: 4px; }

    .serving {
      background: rgba(94, 234, 212, .10); border: 1px solid rgba(94, 234, 212, .28);
      border-radius: 22px; padding: 40px; text-align: center;
    }
    .label { font-size: 15px; letter-spacing: .22em; opacity: .65; }
    .token-huge {
      font-size: 132px; font-weight: 800; line-height: 1.05;
      color: #5eead4; letter-spacing: -.03em; margin: 10px 0;
    }
    .token-huge.dim { color: rgba(234, 250, 247, .25); }
    .token-huge.blink { animation: flash 1.1s ease-in-out infinite; }
    @keyframes flash { 0%, 100% { opacity: 1 } 50% { opacity: .35 } }
    .name { font-size: 30px; font-weight: 500; }

    .upcoming { flex: 1; }
    .chips { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 16px; }
    .chip {
      background: rgba(255, 255, 255, .07); border: 1px solid rgba(255, 255, 255, .12);
      border-radius: 16px; padding: 18px 24px; min-width: 168px;
    }
    .chip.urgent { border-color: #fca5a5; background: rgba(252, 165, 165, .12); }
    .chip-token { font-size: 34px; font-weight: 700; color: #5eead4; }
    .chip.urgent .chip-token { color: #fca5a5; }
    .chip-name { font-size: 17px; margin-top: 4px; }
    .chip-eta { font-size: 14px; opacity: .68; margin-top: 6px; }
    .none { opacity: .55; font-size: 19px; }

    footer { opacity: .5; font-size: 14px; text-align: center; }
  `]
})
export class WaitingBoardComponent {
  /** Route param: /board/:doctorId — a kiosk is pinned to one room. */
  readonly doctorId = input.required<string>();

  private readonly queue = inject(QueueService);
  private readonly destroyRef = inject(DestroyRef);

  readonly snapshot = signal<QueueSnapshot | null>(null);
  readonly now = signal(this.currentTime());

  readonly nowServing = computed(() =>
    this.snapshot()?.entries.find(e => e.status === 'IN_CONSULTATION') ?? null);
  readonly called = computed(() =>
    this.snapshot()?.entries.find(e => e.status === 'CALLED') ?? null);

  /**
   * Whoever the board should shout about: in the room beats just-called.
   *
   * A single computed rather than a chain of template conditions, because
   * Angular's control-flow blocks only allow an `as` alias on the primary
   * branch — and note that its parser scans those blocks even inside HTML
   * comments, so template comments must not mention them by name.
   */
  readonly spotlight = computed(() => this.nowServing() ?? this.called());
  readonly upcoming = computed(() =>
    (this.snapshot()?.entries.filter(e => e.status === 'WAITING') ?? []).slice(0, 8));

  constructor() {
    // Route inputs resolve after construction; poll once then stream.
    queueMicrotask(() => {
      const id = this.doctorId();
      this.queue.board(id).subscribe(s => this.snapshot.set(s));
      this.queue.stream(id)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(s => this.snapshot.set(s));
    });

    const clock = setInterval(() => this.now.set(this.currentTime()), 1000);
    this.destroyRef.onDestroy(() => clearInterval(clock));
  }

  firstName(fullName: string): string {
    return fullName?.split(' ')[0] ?? '';
  }

  private currentTime(): string {
    return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }
}
