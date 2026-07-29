import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProvidersService } from '../../core/providers/providers.service';
import { QueueService, QueueEntry, QueueSnapshot } from '../../core/queue/queue.service';
import { EmptyStateComponent } from '../../shared/ui.components';

/**
 * The doctor's live console. Everything updates over SSE, so two doctors (or a
 * doctor and reception) can work the same queue without stepping on each other.
 */
@Component({
  selector: 'cc-doctor-console',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatIconModule, MatSnackBarModule, EmptyStateComponent],
  template: `
    <div class="cc-page">
      <div class="cc-page-head">
        <div>
          <h1>Live queue</h1>
          <div class="cc-sub">
            @if (snapshot(); as s) {
              {{ s.waiting }} waiting · typical consultation {{ s.averageConsultationMinutes }} min
              <span class="cc-pill ok" style="margin-left:8px">
                <span class="cc-live-dot"></span> live
              </span>
            } @else { Connecting… }
          </div>
        </div>
        <span class="cc-spacer"></span>
        <button mat-flat-button class="cc-btn-primary" style="height:46px;padding:0 22px"
                [disabled]="!hasWaiting()" (click)="callNext()">
          <mat-icon>campaign</mat-icon> Call next patient
        </button>
      </div>

      <!-- Now serving -->
      @if (nowServing(); as current) {
        <div class="cc-card" style="border-left:4px solid var(--cc-primary);margin-bottom:18px">
          <div class="cc-row">
            <div class="cc-token cc-token-lg">{{ current.tokenNumber }}</div>
            <div style="flex:1">
              <div class="cc-faint">IN CONSULTATION</div>
              <div style="font-size:20px;font-weight:600">{{ current.patientName }}</div>
              @if (current.complaint) {
                <div class="cc-muted" style="font-size:14px">{{ current.complaint }}</div>
              }
              <div class="cc-faint">
                Started {{ current.startedAt | date:'h:mm a' }} · waited {{ current.waitedMinutes }} min
              </div>
            </div>
            <button mat-flat-button class="cc-btn-primary" style="height:44px"
                    (click)="act(current, 'complete')">
              <mat-icon>task_alt</mat-icon> Complete consultation
            </button>
          </div>
          <div class="cc-faint" style="margin-top:10px">
            Completing this visit opens the chart, issues the invoice and notifies the patient.
          </div>
        </div>
      }

      <!-- Called, awaiting arrival -->
      @for (entry of called(); track entry.id) {
        <div class="cc-card" style="border-left:4px solid var(--cc-warn);margin-bottom:12px">
          <div class="cc-row">
            <div class="cc-token">{{ entry.tokenNumber }}</div>
            <div style="flex:1">
              <div style="font-weight:600">{{ entry.patientName }}</div>
              <div class="cc-faint">
                Called {{ entry.calledAt | date:'h:mm a' }} · attempt {{ entry.callAttempts }} of 3
              </div>
            </div>
            <button mat-stroked-button (click)="act(entry, 'recall')">
              <mat-icon>campaign</mat-icon> Call again
            </button>
            <button mat-flat-button class="cc-btn-primary" (click)="act(entry, 'start')">
              <mat-icon>login</mat-icon> Start
            </button>
          </div>
        </div>
      }

      <!-- Waiting list -->
      <h4>Waiting</h4>
      @if (waiting().length) {
        <div class="cc-table-wrap">
          @for (entry of waiting(); track entry.id; let i = $index) {
            <div class="cc-queue-row">
              <div class="cc-token" [class.emergency]="entry.priority === 'EMERGENCY'">
                {{ entry.tokenNumber }}
              </div>
              <div style="flex:1">
                <div class="cc-row" style="gap:8px">
                  <span style="font-weight:600">{{ entry.patientName }}</span>
                  @if (entry.priority !== 'NORMAL') {
                    <span class="cc-pill" [class.danger]="entry.priority === 'EMERGENCY'"
                          [class.warn]="entry.priority === 'URGENT'">{{ entry.priority }}</span>
                  }
                </div>
                @if (entry.complaint) {
                  <div class="cc-faint">{{ entry.complaint }}</div>
                }
              </div>
              <div style="text-align:right;min-width:110px">
                <div class="cc-faint">waiting {{ entry.waitedMinutes }} min</div>
                @if (entry.estimatedWaitMinutes !== null) {
                  <div style="font-weight:600;font-size:13px">
                    ~{{ entry.estimatedWaitMinutes }} min to go
                  </div>
                }
              </div>
              <button mat-icon-button (click)="act(entry, 'left')" title="Patient left">
                <mat-icon>person_off</mat-icon>
              </button>
            </div>
          }
        </div>
      } @else {
        <cc-empty icon="done_all" title="Queue is clear"
                  text="No patients are waiting right now." />
      }
    </div>
  `,
  styles: [`
    .cc-token {
      background: var(--cc-primary-light); color: var(--cc-primary-dark);
      font-weight: 700; font-size: 15px; letter-spacing: .02em;
      padding: 8px 12px; border-radius: 10px; min-width: 66px; text-align: center;
    }
    .cc-token.cc-token-lg { font-size: 22px; padding: 14px 18px; min-width: 92px; }
    .cc-token.emergency { background: var(--cc-danger-bg); color: var(--cc-danger); }
    .cc-queue-row {
      display: flex; align-items: center; gap: 14px;
      padding: 12px 16px; border-bottom: 1px solid var(--cc-line); background: var(--cc-surface);
    }
    .cc-queue-row:last-child { border-bottom: none; }
    .cc-live-dot {
      width: 7px; height: 7px; border-radius: 50%; background: currentColor;
      display: inline-block; animation: cc-pulse 1.6s ease-in-out infinite;
    }
    @keyframes cc-pulse { 0%, 100% { opacity: 1 } 50% { opacity: .25 } }
  `]
})
export class DoctorConsoleComponent {
  private readonly queue = inject(QueueService);
  private readonly providers = inject(ProvidersService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly snapshot = signal<QueueSnapshot | null>(null);
  readonly doctorId = signal<string | null>(null);

  readonly nowServing = computed(() =>
    this.snapshot()?.entries.find(e => e.status === 'IN_CONSULTATION') ?? null);
  readonly called = computed(() =>
    this.snapshot()?.entries.filter(e => e.status === 'CALLED') ?? []);
  readonly waiting = computed(() =>
    this.snapshot()?.entries.filter(e => e.status === 'WAITING') ?? []);
  readonly hasWaiting = computed(() => this.waiting().length > 0);

  constructor() {
    this.providers.me().subscribe({
      next: doctor => {
        this.doctorId.set(doctor.id);
        this.connect(doctor.id);
      },
      error: () => this.snackBar.open(
        'No doctor profile is linked to your account', 'OK', { duration: 4000 })
    });
  }

  private connect(doctorId: string): void {
    this.queue.stream(doctorId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(snapshot => this.snapshot.set(snapshot));
  }

  callNext(): void {
    const doctorId = this.doctorId();
    if (!doctorId) { return; }
    this.queue.callNext(doctorId).subscribe({
      next: entry => this.snackBar.open(
        `Calling ${entry.tokenNumber} — ${entry.patientName}`, 'OK', { duration: 3000 }),
      error: err => this.snackBar.open(
        err?.error?.detail ?? 'Nobody is waiting', 'OK', { duration: 3000 })
    });
  }

  act(entry: QueueEntry, action: 'recall' | 'start' | 'complete' | 'left'): void {
    this.queue.action(entry.id, action).subscribe({
      next: () => {
        if (action === 'complete') {
          this.snackBar.open(
            'Consultation complete — chart and invoice created', 'OK', { duration: 4000 });
        }
      },
      error: err => this.snackBar.open(err?.error?.detail ?? 'Action failed', 'OK',
        { duration: 4000 })
    });
  }
}
