import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { QueueService, MyQueueStatus } from '../../core/queue/queue.service';
import { EmptyStateComponent } from '../../shared/ui.components';

/**
 * "Where am I in the queue?" — the patient-facing half of Live Care Flow, and
 * the answer to the single most common question in any waiting room.
 *
 * Polls every 10s rather than holding an SSE stream: a phone in a pocket with
 * a dropped connection recovers cleanly from polling, and the payload is tiny.
 */
@Component({
  selector: 'cc-my-queue',
  standalone: true,
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, EmptyStateComponent],
  template: `
    <div class="cc-page" style="max-width:640px">
      <div class="cc-page-head">
        <div>
          <h1>My place in the queue</h1>
          <div class="cc-sub">Updates automatically while you wait</div>
        </div>
      </div>

      @if (status(); as s) {
        @if (s.inQueue && s.entry; as entry) {
          <div class="cc-card" style="text-align:center;padding:34px 22px">
            <div class="cc-faint" style="letter-spacing:.18em;font-size:12px">YOUR TOKEN</div>
            <div class="token">{{ entry.tokenNumber }}</div>

            @if (entry.status === 'WAITING') {
              <div class="headline">
                @if (entry.position === 0) { You're next }
                @else { {{ entry.position! + 1 }}<span class="ordinal">{{ ordinal(entry.position! + 1) }}</span> in line }
              </div>
              @if (entry.estimatedWaitMinutes !== null) {
                <div class="eta">
                  <mat-icon>schedule</mat-icon>
                  about {{ entry.estimatedWaitMinutes }} minutes
                </div>
              }
              <div class="progress">
                <div class="bar" [style.width.%]="progress(entry.position!)"></div>
              </div>
            } @else if (entry.status === 'CALLED') {
              <div class="headline called">You've been called</div>
              <div class="eta">Please go to {{ entry.doctorName }}'s room</div>
            } @else if (entry.status === 'IN_CONSULTATION') {
              <div class="headline">With the doctor now</div>
            } @else {
              <div class="headline">{{ s.message }}</div>
            }

            <div class="cc-divider"></div>
            <div class="cc-row" style="justify-content:center;gap:24px">
              <div>
                <div class="cc-faint">Doctor</div>
                <div style="font-weight:600">{{ entry.doctorName }}</div>
              </div>
              <div>
                <div class="cc-faint">Checked in</div>
                <div style="font-weight:600">{{ entry.checkedInAt | date:'h:mm a' }}</div>
              </div>
              <div>
                <div class="cc-faint">Waiting</div>
                <div style="font-weight:600">{{ entry.waitedMinutes }} min</div>
              </div>
            </div>
          </div>

          <div class="cc-faint" style="text-align:center;margin-top:14px">
            Estimates come from your doctor's actual consultation times today —
            they shift as the clinic runs ahead or behind.
          </div>
        } @else {
          <cc-empty icon="how_to_reg" title="You're not checked in"
                    text="Check in at reception when you arrive, or from your appointment, and your live position will appear here.">
            <a mat-flat-button class="cc-btn-primary" routerLink="/my-appointments">
              My appointments
            </a>
          </cc-empty>
        }
      }
    </div>
  `,
  styles: [`
    .token {
      font-size: 62px; font-weight: 800; color: var(--cc-primary);
      letter-spacing: -.02em; line-height: 1.1; margin: 6px 0 14px;
    }
    .headline { font-size: 27px; font-weight: 650; }
    .headline.called { color: var(--cc-ok); }
    .ordinal { font-size: 17px; vertical-align: super; margin-left: 2px; }
    .eta {
      display: inline-flex; align-items: center; gap: 6px; margin-top: 8px;
      color: var(--cc-ink-soft); font-size: 16px;
    }
    .progress {
      height: 8px; background: var(--cc-line); border-radius: 999px;
      margin-top: 20px; overflow: hidden;
    }
    .bar {
      height: 100%; background: var(--cc-primary); border-radius: 999px;
      transition: width .6s ease;
    }
  `]
})
export class MyQueueComponent {
  private readonly queue = inject(QueueService);
  private readonly destroyRef = inject(DestroyRef);

  readonly status = signal<MyQueueStatus | null>(null);

  constructor() {
    this.refresh();
    const timer = setInterval(() => this.refresh(), 10_000);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  private refresh(): void {
    this.queue.myStatus().subscribe({
      next: status => this.status.set(status),
      error: () => this.status.set({ inQueue: false, entry: null, message: '' })
    });
  }

  /** Rough visual progress: fuller bar the closer you are to the front. */
  progress(position: number): number {
    return Math.max(6, 100 - Math.min(position, 8) * 12);
  }

  ordinal(n: number): string {
    if (n % 10 === 1 && n % 100 !== 11) { return 'st'; }
    if (n % 10 === 2 && n % 100 !== 12) { return 'nd'; }
    if (n % 10 === 3 && n % 100 !== 13) { return 'rd'; }
    return 'th';
  }
}
