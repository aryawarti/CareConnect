import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProvidersService } from '../../core/providers/providers.service';
import { Doctor, ScheduleException, Slot } from '../../core/providers/provider.models';
import { AvailabilityEditorComponent } from './availability-editor.component';
import { SkeletonComponent, ErrorPanelComponent } from '../../shared/ui.components';

/**
 * A doctor's own schedule: the weekly pattern, and the individual days they are
 * away.
 *
 * This page existed nowhere. The availability editor was reachable only through
 * the administrator's doctor list, so a doctor could not publish their own
 * consulting hours, and a doctor hired through the application flow had no
 * schedule at all — which is precisely why patients were meeting "no slots
 * available" on a clinic that appeared fully staffed.
 *
 * The two halves are deliberately different in kind. Weekly hours are the
 * recurring pattern; time off is the exception to it, and it wins — the booking
 * calculation drops a date entirely when it carries an exception, so this page
 * says that rather than leaving a doctor to wonder whether a leave day still
 * needs the hours removing.
 */
@Component({
  selector: 'cc-my-schedule',
  standalone: true,
  imports: [DatePipe, RouterLink, ReactiveFormsModule, MatCardModule, MatButtonModule,
            MatIconModule, MatFormFieldModule, MatInputModule, MatDatepickerModule,
            MatNativeDateModule, MatSnackBarModule, AvailabilityEditorComponent,
            SkeletonComponent, ErrorPanelComponent],
  template: `
    <div class="cc-page">
      <div class="cc-page-head">
        <div>
          <h2>My schedule</h2>
          <div class="cc-sub">The hours patients can book, and the days you are away</div>
        </div>
      </div>

      <!-- An "as" binding is only legal on the PRIMARY @if (NG5002), so the
           loaded branch leads and the other states follow it. -->
      @if (doctor(); as doc) {
        <cc-availability-editor
          [doctor]="doc"
          [dismissible]="false"
          heading="Weekly consulting hours"
          (saved)="onScheduleSaved($event)" />

        <mat-card appearance="outlined" style="margin-top:24px">
          <mat-card-header>
            <mat-card-title>Time off</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <p class="cc-faint" style="margin-top:0">
              Days you will not be consulting. A date listed here is removed from
              booking entirely, whatever your weekly hours say — so there is no need
              to edit the pattern above for a single day away.
            </p>

            @if (timeOff().length) {
              <div class="cc-timeoff-list">
                @for (day of timeOff(); track day.id) {
                  <div class="cc-timeoff-row">
                    <mat-icon>event_busy</mat-icon>
                    <div>
                      <div style="font-weight:600">{{ day.date | date:'EEEE, d MMMM y' }}</div>
                      @if (day.reason) {
                        <div class="cc-faint" style="font-size:13px">{{ day.reason }}</div>
                      }
                    </div>
                    <span style="flex:1"></span>
                    <button mat-icon-button (click)="removeTimeOff(day)"
                            [attr.aria-label]="'Remove time off on ' + day.date">
                      <mat-icon>delete</mat-icon>
                    </button>
                  </div>
                }
              </div>
            } @else {
              <p class="cc-muted">No upcoming time off.</p>
            }

            <form [formGroup]="form" (ngSubmit)="addTimeOff()"
                  style="display:flex;gap:12px;align-items:baseline;flex-wrap:wrap;margin-top:16px">
              <mat-form-field appearance="outline">
                <mat-label>Date</mat-label>
                <input matInput [matDatepicker]="picker" formControlName="date" [min]="today">
                <mat-datepicker-toggle matIconSuffix [for]="picker" />
                <mat-datepicker #picker />
                @if (form.controls.date.hasError('required') && form.controls.date.touched) {
                  <mat-error>Pick a date</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline" style="min-width:240px">
                <mat-label>Reason (optional)</mat-label>
                <input matInput formControlName="reason" maxlength="200"
                       placeholder="Conference, leave, …">
              </mat-form-field>
              <button mat-stroked-button type="submit" [disabled]="savingTimeOff()">
                <mat-icon>add</mat-icon> Add time off
              </button>
            </form>
          </mat-card-content>
        </mat-card>
      } @else if (loading()) {
        <cc-skeleton [count]="4" variant="card" />
      } @else if (noProfile()) {
        <!-- Not an error the doctor can retry: their account exists but has no
             professional profile yet, so there is nothing to schedule. Send them
             to the step that unblocks it. -->
        <mat-card appearance="outlined">
          <mat-card-content style="padding:28px;text-align:center">
            <mat-icon class="cc-empty-icon">badge</mat-icon>
            <h3 style="margin:12px 0 6px">Complete your professional profile first</h3>
            <p class="cc-faint" style="max-width:460px;margin:0 auto 20px">
              Your consulting hours attach to your doctor profile. Submit your
              credentials and, once administration approves them, you can publish
              the hours patients book against.
            </p>
            <a mat-flat-button color="primary" routerLink="/doctor-application">
              Go to my professional profile
            </a>
          </mat-card-content>
        </mat-card>
      } @else if (error()) {
        <cc-error [message]="error()!" (retry)="load()" />
      }
    </div>
  `,
  styles: [`
    .cc-empty-icon { font-size: 40px; width: 40px; height: 40px; color: var(--cc-primary); }
    .cc-timeoff-list { display: flex; flex-direction: column; }
    .cc-timeoff-row {
      display: flex; gap: 12px; align-items: center;
      padding: 10px 0; border-bottom: 1px solid var(--cc-border, #e2e8f0);
    }
    .cc-timeoff-row mat-icon { color: #c2410c; }
  `]
})
export class MyScheduleComponent {
  private readonly providers = inject(ProvidersService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  readonly doctor = signal<Doctor | null>(null);
  readonly timeOff = signal<ScheduleException[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly savingTimeOff = signal(false);

  /** A 404 from /providers/me is not a failure — it means this account has no
   *  doctor profile yet, which needs a different screen, not a retry button. */
  readonly noProfile = signal(false);

  readonly today = new Date();

  readonly form = this.fb.nonNullable.group({
    date: [null as Date | null, Validators.required],
    reason: ['']
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.noProfile.set(false);
    this.providers.me().subscribe({
      next: doc => {
        this.doctor.set(doc);
        this.loading.set(false);
        this.loadTimeOff(doc.id);
      },
      error: err => {
        this.loading.set(false);
        if (err?.status === 404) {
          this.noProfile.set(true);
          this.error.set('No doctor profile');
        } else {
          this.error.set(err?.error?.detail ?? 'Could not load your schedule.');
        }
      }
    });
  }

  private loadTimeOff(doctorId: string): void {
    this.providers.timeOff(doctorId).subscribe({
      next: list => this.timeOff.set(list),
      error: () => this.timeOff.set([])
    });
  }

  onScheduleSaved(slots: Slot[]): void {
    // Nothing to reload, but the count is worth confirming: a doctor who saves an
    // empty schedule has just made themselves unbookable and should know it.
    if (!slots.length) {
      this.snackBar.open(
        'Your schedule is empty — patients cannot book you until you add hours',
        'OK', { duration: 6000 });
    }
  }

  addTimeOff(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const doc = this.doctor();
    const date = this.form.controls.date.value;
    if (!doc || !date) {
      return;
    }
    this.savingTimeOff.set(true);
    // Local date, not toISOString(): that converts to UTC first, which moves the
    // date back a day for anyone east of Greenwich — including every user of a
    // clinic in India.
    const iso = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
      + `-${String(date.getDate()).padStart(2, '0')}`;

    this.providers.addTimeOff(doc.id, iso, this.form.controls.reason.value).subscribe({
      next: () => {
        this.savingTimeOff.set(false);
        this.form.reset({ date: null, reason: '' });
        this.loadTimeOff(doc.id);
        this.snackBar.open('Time off added', 'OK', { duration: 3000 });
      },
      error: err => {
        this.savingTimeOff.set(false);
        this.snackBar.open(err?.error?.detail ?? 'Could not add time off', 'OK',
          { duration: 4000 });
      }
    });
  }

  removeTimeOff(day: ScheduleException): void {
    const doc = this.doctor();
    if (!doc) {
      return;
    }
    this.providers.removeTimeOff(doc.id, day.id).subscribe({
      next: () => {
        this.loadTimeOff(doc.id);
        this.snackBar.open('Time off removed', 'OK', { duration: 3000 });
      },
      error: err => this.snackBar.open(
        err?.error?.detail ?? 'Could not remove time off', 'OK', { duration: 4000 })
    });
  }
}
