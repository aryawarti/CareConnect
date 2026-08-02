import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProvidersService } from '../../core/providers/providers.service';
import {
  DAY_NAMES_LONG, Doctor, Slot, formatTime, groupByDay
} from '../../core/providers/provider.models';

/**
 * The weekly consulting pattern for one doctor: which days, which hours, and
 * how long an appointment is.
 *
 * This is the screen the whole booking flow depends on. A doctor with no windows
 * here cannot be booked at all — the patient's date picker will refuse every
 * date — so the empty state says that in as many words rather than leaving the
 * consequence to be discovered from the other side.
 *
 * Used both by an administrator managing any doctor and by a doctor managing
 * their own schedule, which is why the close button is optional.
 */
@Component({
  selector: 'cc-availability-editor',
  standalone: true,
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatButtonModule, MatIconModule, MatListModule, MatSnackBarModule],
  template: `
    <mat-card appearance="outlined" style="margin-top:24px">
      <mat-card-header>
        <mat-card-title>{{ heading() }}</mat-card-title>
        <span style="flex:1"></span>
        @if (dismissible()) {
          <button mat-icon-button (click)="closed.emit()" aria-label="Close">
            <mat-icon>close</mat-icon>
          </button>
        }
      </mat-card-header>

      <mat-card-content>
        @if (!slots().length) {
          <!-- Not a neutral "nothing here": an empty schedule is the single
               reason a patient sees "no slots available", so it names the
               consequence and the fix. -->
          <div class="cc-empty-warn">
            <mat-icon>event_busy</mat-icon>
            <div>
              <strong>No consulting hours set — this doctor cannot be booked.</strong>
              <div class="cc-faint" style="margin-top:4px">
                Patients searching for this doctor will find no available slots on any
                date. Add at least one window below, then save.
              </div>
            </div>
          </div>

          <button mat-stroked-button type="button" (click)="applyWeekdayPreset()"
                  style="margin-bottom:8px">
            <mat-icon>bolt</mat-icon> Use a standard week (Mon–Fri, 9–1 &amp; 2–5)
          </button>
        } @else {
          <!-- Grouped by day so it reads as a week, not a flat list of rows -->
          @for (group of grouped(); track group.day) {
            <div class="cc-day-row">
              <div class="cc-day-name">{{ dayName(group.day) }}</div>
              <div class="cc-day-windows">
                @for (slot of group.windows; track slot) {
                  <span class="cc-window-chip">
                    {{ time(slot.startTime) }} – {{ time(slot.endTime) }}
                    <span class="cc-faint">· {{ slot.slotMinutes }} min</span>
                    <button mat-icon-button (click)="remove(slot)"
                            [attr.aria-label]="'Remove ' + dayName(group.day) + ' ' + slot.startTime">
                      <mat-icon>close</mat-icon>
                    </button>
                  </span>
                }
              </div>
            </div>
          }
          <div class="cc-faint" style="margin-top:10px">
            {{ appointmentsPerWeek() }} appointment slots per week.
          </div>
        }

        <div class="cc-divider"></div>

        <form [formGroup]="form" (ngSubmit)="add()"
              style="display:flex;gap:12px;align-items:baseline;flex-wrap:wrap">
          <mat-form-field appearance="outline">
            <mat-label>Day</mat-label>
            <mat-select formControlName="dayOfWeek">
              @for (d of [1,2,3,4,5,6,7]; track d) {
                <mat-option [value]="d">{{ dayName(d) }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>From</mat-label>
            <input matInput type="time" formControlName="startTime">
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>To</mat-label>
            <input matInput type="time" formControlName="endTime">
          </mat-form-field>
          <mat-form-field appearance="outline" style="width:120px">
            <mat-label>Slot (min)</mat-label>
            <input matInput type="number" formControlName="slotMinutes">
          </mat-form-field>
          <button mat-stroked-button type="submit">
            <mat-icon>add</mat-icon> Add window
          </button>
        </form>

        <div class="cc-row" style="margin-top:16px;gap:12px;align-items:center">
          <button mat-flat-button color="primary" type="button"
                  [disabled]="!dirty() || saving()" (click)="saveAll()">
            {{ saving() ? 'Saving…' : 'Save schedule' }}
          </button>
          @if (dirty()) {
            <!-- Without this, adding windows and navigating away silently loses
                 them: nothing is sent to the server until Save. -->
            <span class="cc-unsaved">
              <mat-icon>edit</mat-icon> Unsaved changes
            </span>
            <button mat-button type="button" (click)="revert()">Discard</button>
          } @else if (loaded()) {
            <span class="cc-saved"><mat-icon>check_circle</mat-icon> Saved</span>
          }
        </div>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .cc-empty-warn {
      display: flex; gap: 12px; align-items: flex-start;
      background: var(--cc-warn-bg, #fff7ed); border: 1px solid var(--cc-warn-border, #fed7aa);
      border-radius: 10px; padding: 14px 16px; margin-bottom: 16px;
    }
    .cc-empty-warn mat-icon { color: #c2410c; flex: none; }
    .cc-day-row {
      display: flex; gap: 16px; align-items: baseline;
      padding: 10px 0; border-bottom: 1px solid var(--cc-border, #e2e8f0);
    }
    .cc-day-name { width: 104px; font-weight: 600; flex: none; }
    .cc-day-windows { display: flex; flex-wrap: wrap; gap: 8px; }
    .cc-window-chip {
      display: inline-flex; align-items: center; gap: 6px;
      background: var(--cc-chip-bg, #f1f5f9); border-radius: 999px;
      padding: 2px 4px 2px 14px; font-size: 14px;
    }
    .cc-window-chip button { width: 28px; height: 28px; }
    .cc-window-chip mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .cc-unsaved, .cc-saved { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; }
    .cc-unsaved { color: #c2410c; }
    .cc-saved { color: var(--cc-success, #15803d); }
    .cc-unsaved mat-icon, .cc-saved mat-icon {
      font-size: 17px; width: 17px; height: 17px;
    }
    @media (max-width: 600px) {
      .cc-day-row { flex-direction: column; gap: 6px; }
      .cc-day-name { width: auto; }
    }
  `]
})
export class AvailabilityEditorComponent {
  readonly doctor = input.required<Doctor>();
  /** Admin opens this inside a list and needs to close it; a doctor on their
   *  own schedule page has nothing to close. */
  readonly dismissible = input(true);
  readonly heading = input('Weekly consulting hours');
  readonly closed = output<void>();
  /** Lets a host page react to the schedule becoming non-empty — the doctor
   *  dashboard uses it to drop its "set your hours" prompt. */
  readonly saved = output<Slot[]>();

  private readonly service = inject(ProvidersService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  readonly slots = signal<Slot[]>([]);
  readonly saving = signal(false);
  readonly loaded = signal(false);
  /** What the server last confirmed, so "unsaved" is a fact rather than a guess. */
  private readonly persisted = signal<string>('[]');

  readonly grouped = computed(() => groupByDay(this.slots()));
  readonly dirty = computed(() => this.fingerprint(this.slots()) !== this.persisted());

  readonly appointmentsPerWeek = computed(() =>
    this.slots().reduce((total, s) => total + this.slotsIn(s), 0));

  readonly form = this.fb.nonNullable.group({
    dayOfWeek: [1, Validators.required],
    startTime: ['09:00', Validators.required],
    endTime: ['13:00', Validators.required],
    slotMinutes: [30, [Validators.min(5), Validators.max(240)]]
  });

  constructor() {
    effect(() => {
      const doc = this.doctor();
      this.loaded.set(false);
      this.service.availability(doc.id).subscribe(s => {
        this.slots.set(s);
        this.persisted.set(this.fingerprint(s));
        this.loaded.set(true);
      });
    });
  }

  dayName(d: number): string {
    return DAY_NAMES_LONG[d];
  }

  time(value: string): string {
    return formatTime(value);
  }

  /** Order-independent so re-sorting alone never counts as an edit. */
  private fingerprint(slots: Slot[]): string {
    return JSON.stringify(slots
      .map(s => `${s.dayOfWeek}|${s.startTime}|${s.endTime}|${s.slotMinutes}`)
      .sort());
  }

  private slotsIn(slot: Slot): number {
    const minutes = (t: string) => {
      const [h, m] = t.split(':').map(Number);
      return h * 60 + m;
    };
    const span = minutes(slot.endTime) - minutes(slot.startTime);
    return span > 0 && slot.slotMinutes > 0 ? Math.floor(span / slot.slotMinutes) : 0;
  }

  add(): void {
    const v = this.form.getRawValue();
    if (v.startTime >= v.endTime) {
      this.snackBar.open('Start time must be before end time', 'OK', { duration: 3000 });
      return;
    }
    // Caught here as well as by the server: the backend rejects the whole save
    // with OverlappingSlotException, which would discard every other edit made
    // in this session. Better to refuse the one window that is wrong.
    const clash = this.slots().find(s => s.dayOfWeek === v.dayOfWeek
      && v.startTime < s.endTime && s.startTime < v.endTime);
    if (clash) {
      this.snackBar.open(
        `Overlaps an existing ${this.dayName(v.dayOfWeek)} window ` +
        `(${this.time(clash.startTime)}–${this.time(clash.endTime)})`, 'OK', { duration: 4000 });
      return;
    }
    this.slots.update(s => [...s, { ...v }]);
  }

  remove(slot: Slot): void {
    this.slots.update(list => list.filter(s => s !== slot));
  }

  revert(): void {
    this.service.availability(this.doctor().id).subscribe(s => {
      this.slots.set(s);
      this.persisted.set(this.fingerprint(s));
    });
  }

  /** The overwhelmingly common pattern, as one click instead of ten forms. */
  applyWeekdayPreset(): void {
    const week: Slot[] = [];
    for (let day = 1; day <= 5; day++) {
      week.push({ dayOfWeek: day, startTime: '09:00', endTime: '13:00', slotMinutes: 30 });
      week.push({ dayOfWeek: day, startTime: '14:00', endTime: '17:00', slotMinutes: 30 });
    }
    this.slots.set(week);
  }

  saveAll(): void {
    this.saving.set(true);
    this.service.replaceAvailability(this.doctor().id, this.slots()).subscribe({
      next: saved => {
        this.slots.set(saved);
        this.persisted.set(this.fingerprint(saved));
        this.saving.set(false);
        this.saved.emit(saved);
        this.snackBar.open(
          saved.length ? 'Schedule saved — patients can book these hours' : 'Schedule cleared',
          'OK', { duration: 3000 });
      },
      error: err => {
        this.saving.set(false);
        this.snackBar.open(
          err?.error?.detail ?? 'Save failed — check for overlapping windows', 'OK',
          { duration: 4000 });
      }
    });
  }
}
