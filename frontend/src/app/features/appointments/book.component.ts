import { Component, computed, effect, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProvidersService } from '../../core/providers/providers.service';
import { DAY_NAMES_LONG } from '../../core/providers/provider.models';
import { AppointmentsService } from '../../core/appointments/appointments.service';
import { asyncResource, deferredResource } from '../../core/http/async-resource';
import { humanizeError } from '../../core/http/http-status';
import { ErrorPanelComponent, SkeletonComponent } from '../../shared/ui.components';

/**
 * Booking — the flagship patient action, and the screen where the four async
 * states matter most.
 *
 * Slot loading is a `deferredResource`: until a doctor and date are chosen there
 * is nothing to fetch, so the screen says so instead of showing a skeleton for a
 * request it never made. Once chosen, "no free slots" is only claimed after the
 * answer actually arrives — previously the same blank state covered loading,
 * empty and failed alike, so the screen confidently announced "No free slots"
 * while it was still asking.
 *
 * The 409 path is the interesting one: losing a race for a slot is a *business*
 * outcome, so it is reported inline next to the button, and the slot list
 * refreshes so the next choice is accurate. That follows the policy documented
 * in error.interceptor.ts — system errors toast globally, 4xx report where the
 * user was looking — which this screen used to contradict by toasting everything.
 */
@Component({
  selector: 'cc-book',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatButtonModule, MatChipsModule, MatIconModule,
            MatSnackBarModule, SkeletonComponent, ErrorPanelComponent],
  template: `
    <div class="cc-page cc-narrow">
      <div class="cc-page-head">
        <div>
          <h1>Book an appointment</h1>
          <div class="cc-sub">Pick a doctor, a date, and a free slot</div>
        </div>
      </div>

      <div class="cc-card">
        @if (doctors.loading()) {
          <cc-skeleton [count]="2" label="Loading doctors…" />
        } @else if (doctors.failed()) {
          <cc-error [message]="doctors.error()!" (retry)="doctors.reload()" />
        } @else {
          <div class="cc-stack">
            <mat-form-field appearance="outline">
              <mat-label>Doctor</mat-label>
              <mat-select [formControl]="doctorCtl" required>
                @for (d of doctors.value(); track d.id) {
                  <mat-option [value]="d.id">
                    Dr. {{ d.firstName }} {{ d.lastName }} — {{ d.specialty }}
                  </mat-option>
                }
              </mat-select>
              @if (doctorCtl.touched && doctorCtl.hasError('required')) {
                <mat-error>Choose the doctor you want to see</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Date</mat-label>
              <input matInput type="date" [formControl]="dateCtl" [min]="today" required>
              <mat-hint>Today or later</mat-hint>
              @if (dateCtl.touched && dateCtl.hasError('required')) {
                <mat-error>Pick a date</mat-error>
              }
            </mat-form-field>

            <!-- Slots: idle / loading / error / empty / ready -->
            <div>
              <h4 style="margin-top:4px">Available times</h4>
              @if (slots.idle()) {
                <p class="cc-muted">Choose a doctor and a date to see free slots.</p>
              } @else if (slots.loading()) {
                <cc-skeleton [count]="1" label="Finding free slots…" />
              } @else if (slots.failed()) {
                <cc-error [message]="slots.error()!" (retry)="slots.reload()" />
              } @else if (slots.value()?.length) {
                <mat-chip-listbox [formControl]="slotCtl" [class.cc-stale]="slots.refreshing()"
                                  aria-label="Available appointment times">
                  @for (s of slots.value(); track s.startAt) {
                    <mat-chip-option [value]="s.startAt">
                      {{ s.startAt | date:'shortTime' }}
                    </mat-chip-option>
                  }
                </mat-chip-listbox>
              } @else {
                <!-- "No slots" has three quite different causes and the patient
                     can only act on two of them. Saying which one applies is the
                     difference between a dead end and a next step. -->
                <div class="cc-alert cc-alert-warn" role="status">
                  <mat-icon>{{ emptyReason().icon }}</mat-icon>
                  <div>
                    <strong>{{ emptyReason().title }}</strong>
                    <div class="cc-faint" style="margin-top:4px">{{ emptyReason().detail }}</div>
                    @if (emptyReason().suggestDate; as next) {
                      <button mat-stroked-button type="button" style="margin-top:12px"
                              (click)="jumpTo(next)">
                        Try {{ next | date:'EEEE, d MMM' }}
                      </button>
                    }
                  </div>
                </div>
              }
            </div>

            <mat-form-field appearance="outline">
              <mat-label>Reason for the visit (optional)</mat-label>
              <input matInput [formControl]="reasonCtl" maxlength="500">
              <mat-hint align="end">{{ reasonCtl.value.length }}/500</mat-hint>
            </mat-form-field>

            @if (bookingError(); as problem) {
              <cc-error [message]="problem" [canRetry]="false" />
            }

            <!-- Single mat-icon at the root, not a conditional pair: Material
                 projects the icon into its own slot, and a @if with two root
                 nodes silently breaks that projection. The label changes with
                 the state, so the busy state is announced, not just animated. -->
            <button mat-flat-button class="cc-btn-primary" style="height:46px"
                    [disabled]="!canSubmit()" [attr.aria-busy]="submitting()"
                    (click)="book()">
              <mat-icon>{{ submitting() ? 'hourglass_top' : 'event_available' }}</mat-icon>
              {{ submitting() ? 'Booking…' : 'Book appointment' }}
            </button>
          </div>
        }
      </div>
    </div>
  `
})
export class BookComponent {
  private readonly providers = inject(ProvidersService);
  private readonly appointments = inject(AppointmentsService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly today = new Date().toISOString().slice(0, 10);

  readonly doctorCtl = new FormControl('', { nonNullable: true, validators: Validators.required });
  readonly dateCtl = new FormControl(this.today, {
    nonNullable: true, validators: Validators.required
  });
  readonly slotCtl = new FormControl<string | null>(null);
  readonly reasonCtl = new FormControl('', { nonNullable: true });

  readonly doctors = asyncResource(() =>
    this.providers.directory('', 0, 100).pipe(map(r => r.data)));

  /** Idle until both inputs are chosen — see the class comment. */
  readonly slots = deferredResource(() =>
    this.appointments.available(this.doctorCtl.value, this.dateCtl.value));

  readonly submitting = signal(false);
  readonly bookingError = signal<string | null>(null);

  /** The chosen doctor, as the directory described them — carries workingDays. */
  readonly chosenDoctor = computed(() =>
    this.doctors.value()?.find(d => d.id === this.doctorIdSignal()) ?? null);

  /**
   * Why there are no slots, in terms the patient can act on.
   *
   * "No free slots on this date" was true but useless: it reads as "this doctor
   * is busy" whether the doctor never consults on Tuesdays, is on leave that
   * day, or is genuinely fully booked. Only the last is worth waiting for; the
   * first two need a different date, and the first can be answered without
   * asking the server at all.
   */
  readonly emptyReason = computed((): {
    icon: string; title: string; detail: string; suggestDate: string | null;
  } => {
    const doctor = this.chosenDoctor();
    const date = this.dateCtl.value;

    if (doctor && !doctor.bookable) {
      return {
        icon: 'event_busy',
        title: 'This doctor has no published consulting hours',
        detail: 'Nobody has set their schedule yet, so no date can be booked. '
              + 'Try another doctor in the same department.',
        suggestDate: null
      };
    }

    const weekday = date ? new Date(`${date}T00:00:00`).getDay() : -1;
    // JS Sunday=0; the API and DAY_NAMES use ISO where Sunday=7.
    const isoDay = weekday === 0 ? 7 : weekday;

    if (doctor && doctor.workingDays.length && !doctor.workingDays.includes(isoDay)) {
      return {
        icon: 'event_busy',
        title: `Dr. ${doctor.lastName} does not consult on ${DAY_NAMES_LONG[isoDay]}s`,
        detail: `They consult on ${doctor.workingDays.map(d => DAY_NAMES_LONG[d]).join(', ')}.`,
        suggestDate: this.nextWorkingDate(doctor.workingDays)
      };
    }

    return {
      icon: 'event_available',
      title: 'Fully booked on this date',
      detail: 'Every slot on this day has been taken. Another date is likely to have room.',
      suggestDate: this.nextWorkingDate(doctor?.workingDays ?? [], 1)
    };
  });

  /**
   * Bridges the reactive form into signal-land. A FormControl is not a signal,
   * so reading slotCtl.value from a computed would never re-evaluate.
   */
  private readonly slotChosen = signal(false);
  readonly canSubmit = computed(() => !this.submitting() && this.slotChosen());

  /** Mirrors doctorCtl into signal-land, for the same reason as slotChosen. */
  private readonly doctorIdSignal = signal('');

  constructor() {
    // takeUntilDestroyed: these outlive the component otherwise, and a stale
    // subscription writing to a destroyed component's signals is a real leak.
    this.doctorCtl.valueChanges.pipe(takeUntilDestroyed()).subscribe(id => {
      this.doctorIdSignal.set(id);
      this.refreshSlots();
    });
    this.dateCtl.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.refreshSlots());
    this.slotCtl.valueChanges.pipe(takeUntilDestroyed())
      .subscribe(value => this.slotChosen.set(!!value));

    // Arriving from a doctor's profile or a directory card: honour the choice
    // already made rather than presenting an empty picker again. Applied once
    // the directory has loaded, since the select needs its options to exist.
    const preselected = this.route.snapshot.queryParamMap.get('doctorId');
    if (preselected) {
      effect(() => {
        if (this.doctors.value()?.some(d => d.id === preselected) && !this.doctorCtl.value) {
          this.doctorCtl.setValue(preselected);
        }
      });
    }
  }

  /**
   * The next date this doctor actually consults, so the empty state can offer a
   * date that will work instead of inviting the patient to guess again.
   * Looks two weeks ahead, which covers any weekly pattern.
   */
  private nextWorkingDate(workingDays: number[], skipDays = 1): string | null {
    if (!workingDays.length) {
      return null;
    }
    const from = new Date(`${this.dateCtl.value}T00:00:00`);
    for (let i = skipDays; i <= 14; i++) {
      const candidate = new Date(from);
      candidate.setDate(from.getDate() + i);
      const iso = candidate.getDay() === 0 ? 7 : candidate.getDay();
      if (workingDays.includes(iso)) {
        return `${candidate.getFullYear()}-${String(candidate.getMonth() + 1).padStart(2, '0')}`
             + `-${String(candidate.getDate()).padStart(2, '0')}`;
      }
    }
    return null;
  }

  jumpTo(date: string): void {
    this.dateCtl.setValue(date);
  }

  private refreshSlots(): void {
    this.slotCtl.setValue(null);
    this.bookingError.set(null);
    if (this.doctorCtl.valid && this.dateCtl.valid && this.doctorCtl.value && this.dateCtl.value) {
      this.slots.reload();
    } else {
      this.slots.reset();
    }
  }

  book(): void {
    const slot = this.slotCtl.value;
    if (!slot || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.bookingError.set(null);
    this.appointments.book(this.doctorCtl.value, slot, this.reasonCtl.value).subscribe({
      next: appointment => {
        this.snackBar.open(
          `Booked with ${appointment.doctorName} — awaiting confirmation`, 'OK',
          { duration: 4000 });
        this.router.navigate(['/my-appointments']);
      },
      error: err => {
        this.submitting.set(false);
        // Inline, not a toast: this is about the choice they just made. A 409
        // means someone took the slot first, so refresh the list — otherwise
        // they retry against times that are already gone.
        this.bookingError.set(humanizeError(err));
        this.slots.reload();
      }
    });
  }
}
