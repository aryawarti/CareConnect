import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AppointmentsService } from '../../core/appointments/appointments.service';
import { ProvidersService } from '../../core/providers/providers.service';
import { RecordsService } from '../../core/records/records.service';
import { Appointment } from '../../core/appointments/appointment.models';
import { Doctor } from '../../core/providers/provider.models';
import { Encounter } from '../../core/records/record.models';
import { humanizeError } from '../../core/http/http-status';
import { StatComponent, EmptyStateComponent, ErrorPanelComponent } from '../../shared/ui.components';

/** A doctor's working day: who's coming, and which charts still need signing. */
@Component({
  selector: 'cc-doctor-dashboard',
  standalone: true,
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, MatSnackBarModule,
            StatComponent, EmptyStateComponent, ErrorPanelComponent],
  template: `
    <div class="cc-page">
      @if (loadError(); as problem) {
        <div style="margin-bottom:18px">
          <cc-error [message]="problem + ' Some of today’s information may be missing.'"
                    (retry)="reloadAll()" />
        </div>
      }

      @if (needsApplication()) {
        <div class="cc-card" style="border-left:4px solid var(--cc-warn);margin-bottom:18px">
          <div class="cc-row">
            <mat-icon style="color:var(--cc-warn)">assignment_ind</mat-icon>
            <div style="flex:1">
              <div style="font-weight:600">Complete your professional profile</div>
              <div class="cc-muted" style="font-size:14px">
                Submit your qualification and registration number so the hospital can verify
                you. Patients can't book you until that's done.
              </div>
            </div>
            <a mat-flat-button class="cc-btn-primary" routerLink="/doctor-application">
              Submit credentials
            </a>
          </div>
        </div>
      } @else if (profile() && profile()!.verification === 'PENDING') {
        <div class="cc-card" style="border-left:4px solid var(--cc-warn);margin-bottom:18px">
          <div class="cc-row">
            <mat-icon style="color:var(--cc-warn)">hourglass_top</mat-icon>
            <div style="flex:1">
              <div style="font-weight:600">Your profile is awaiting verification</div>
              <div class="cc-muted" style="font-size:14px">
                Once the administration approves it, you'll appear in the directory and
                start receiving appointment requests.
              </div>
            </div>
          </div>
        </div>
      } @else if (hasSchedule() === false) {
        <!-- The last step of onboarding, and the one nothing used to mention.
             An approved doctor with no published hours is invisible to booking:
             they appear in the directory and every date a patient tries comes
             back empty. This is the prompt that closes that gap. -->
        <div class="cc-card" style="border-left:4px solid var(--cc-warn);margin-bottom:18px">
          <div class="cc-row">
            <mat-icon style="color:var(--cc-warn)">event_busy</mat-icon>
            <div style="flex:1">
              <div style="font-weight:600">Publish your consulting hours</div>
              <div class="cc-muted" style="font-size:14px">
                You have no weekly schedule, so patients cannot book you on any date.
                Set the days and times you consult and you will start receiving requests.
              </div>
            </div>
            <a mat-flat-button class="cc-btn-primary" routerLink="/my-schedule">
              Set my hours
            </a>
          </div>
        </div>
      }

      <div class="cc-page-head">
        <div>
          <h1>{{ profile() ? 'Dr. ' + profile()!.lastName : 'Your day' }}</h1>
          <div class="cc-sub">
            {{ today | date:'EEEE, MMMM d' }}
            @if (profile()) { · {{ profile()!.specialty }} }
          </div>
        </div>
        <span class="cc-spacer"></span>
        <a mat-stroked-button routerLink="/my-schedule">
          <mat-icon>event_available</mat-icon> My hours
        </a>
        <a mat-stroked-button routerLink="/schedule"><mat-icon>calendar_month</mat-icon> Day view</a>
        <a mat-flat-button class="cc-btn-primary" routerLink="/records">
          <mat-icon>clinical_notes</mat-icon> Charts
        </a>
      </div>

      <div class="cc-grid cc-grid-4" style="margin-bottom:22px">
        <cc-stat icon="event" [value]="todayList().length" label="Patients today"
                 [hint]="nextPatientHint()" />
        <cc-stat icon="mark_email_unread" [value]="requests().length"
                 label="Requests to review" tone="accent"
                 hint="Awaiting your decision" />
        <cc-stat icon="pending_actions" [value]="openCharts().length"
                 label="Charts to complete" tone="info"
                 hint="Unsigned encounters" />
        <cc-stat icon="task_alt" [value]="signedCount()" label="Signed records" tone="ok" />
      </div>

      @if (requests().length) {
        <div class="cc-card" style="border-left:4px solid var(--cc-warn);margin-bottom:16px">
          <div class="cc-row">
            <mat-icon style="color:var(--cc-warn)">pending_actions</mat-icon>
            <h3 style="flex:1">
              {{ requests().length }} appointment request{{ requests().length > 1 ? 's' : '' }}
              waiting for you
            </h3>
          </div>
          <div class="cc-stack" style="margin-top:12px">
            @for (r of requests(); track r.id) {
              <div class="cc-row" style="padding:12px 0;border-bottom:1px solid var(--cc-line)">
                <div style="flex:1">
                  <div style="font-weight:600">{{ r.patientName }}</div>
                  <div class="cc-faint">
                    {{ r.startAt | date:'EEE, MMM d · h:mm a' }}
                    @if (r.reason) { · {{ r.reason }} }
                  </div>
                </div>
                <button mat-stroked-button color="warn" (click)="decide(r, 'decline')">
                  Decline
                </button>
                <button mat-flat-button class="cc-btn-primary" (click)="decide(r, 'acceptance')">
                  <mat-icon>check</mat-icon> Accept
                </button>
              </div>
            }
          </div>
          <div class="cc-faint" style="margin-top:10px">
            Accepting confirms the booking for the patient. Declining frees the slot and
            notifies them.
          </div>
        </div>
      }

      <div class="cc-grid cc-grid-2">
        <div class="cc-card">
          <div class="cc-row">
            <h3 style="flex:1">Today's patients</h3>
            <a mat-button routerLink="/schedule">Full schedule</a>
          </div>
          @if (todayList().length) {
            <div class="cc-timeline" style="margin-top:14px">
              @for (a of todayList(); track a.id) {
                <div class="cc-timeline-item">
                  <div class="cc-row">
                    <div style="flex:1">
                      <div style="font-weight:600">{{ a.patientName }}</div>
                      <div class="cc-faint">
                        {{ a.startAt | date:'h:mm a' }}
                        @if (a.reason) { · {{ a.reason }} }
                      </div>
                    </div>
                    <span class="cc-pill" [class]="a.status">{{ a.status }}</span>
                    @if (a.status === 'CONFIRMED') {
                      <button mat-stroked-button (click)="complete(a)">Complete</button>
                    }
                  </div>
                </div>
              }
            </div>
          } @else {
            <cc-empty icon="event_available" title="No patients scheduled today"
                      text="Appointments booked for you will appear here." />
          }
        </div>

        <div class="cc-card">
          <div class="cc-row">
            <h3 style="flex:1">Charts awaiting notes</h3>
            <a mat-button routerLink="/records">Open charts</a>
          </div>
          @if (openCharts().length) {
            <div class="cc-stack" style="margin-top:12px">
              @for (e of openCharts().slice(0, 5); track e.id) {
                <div class="cc-row" style="padding:10px 0;border-bottom:1px solid var(--cc-line)">
                  <div style="flex:1">
                    <div style="font-weight:600">{{ e.patientName }}</div>
                    <div class="cc-faint">{{ e.occurredAt | date:'MMM d, h:mm a' }}</div>
                  </div>
                  <span class="cc-pill OPEN">OPEN</span>
                  <a mat-stroked-button routerLink="/records">Document</a>
                </div>
              }
            </div>
          } @else {
            <cc-empty icon="task_alt" title="Nothing outstanding"
                      text="Every completed visit has a signed chart." />
          }
        </div>
      </div>
    </div>
  `
})
export class DoctorDashboardComponent {
  private readonly appointments = inject(AppointmentsService);
  private readonly providers = inject(ProvidersService);
  private readonly recordsApi = inject(RecordsService);
  private readonly snackBar = inject(MatSnackBar);

  readonly today = new Date();
  readonly profile = signal<Doctor | null>(null);
  readonly todayList = signal<Appointment[]>([]);
  readonly encounters = signal<Encounter[]>([]);

  readonly requests = signal<Appointment[]>([]);
  /** DOCTOR account with no provider profile yet — they must apply first. */
  readonly needsApplication = signal(false);
  /**
   * A real failure, as opposed to an empty result. Shown as a banner rather than
   * per-card because this screen loads four things and any of them failing means
   * the day shown is incomplete — which a doctor needs told, not hidden.
   */
  readonly loadError = signal<string | null>(null);
  /** null until known: the prompt must not flash before the answer arrives. */
  readonly hasSchedule = signal<boolean | null>(null);
  readonly openCharts = computed(() => this.encounters().filter(e => e.status === 'OPEN'));
  readonly signedCount = computed(() =>
    this.encounters().filter(e => e.status !== 'OPEN').length);

  constructor() {
    this.reloadAll();
  }

  reloadAll(): void {
    this.loadError.set(null);
    this.providers.me().subscribe({
      next: doctor => {
        this.profile.set(doctor);
        this.loadDay(doctor.id);
        this.providers.availability(doctor.id).subscribe({
          next: slots => this.hasSchedule.set(slots.length > 0),
          // Unknown, not "missing": prompting a doctor who already has hours to
          // set them would be worse than staying quiet.
          error: () => this.hasSchedule.set(null)
        });
      },
      error: (err: HttpErrorResponse) => {
        // Only a 404 means "this account has no doctor profile yet". Treating
        // every failure as one sent a fully-onboarded doctor to the credentials
        // form whenever provider-service was briefly unavailable.
        if (err.status === 404) {
          this.needsApplication.set(true);
        } else {
          this.loadError.set(humanizeError(err));
        }
      }
    });
    this.reloadEncounters();
    this.loadRequests();
  }

  private reloadEncounters(): void {
    this.recordsApi.doctorEncounters(0, 50).subscribe({
      next: r => this.encounters.set(r.data),
      error: err => this.loadError.set(humanizeError(err))
    });
  }

  private loadRequests(): void {
    this.appointments.doctorRequests().subscribe({
      next: list => this.requests.set(list),
      // Was `set([])`, which rendered a failed request as "no pending requests" —
      // so a doctor could silently miss bookings waiting on their decision.
      error: err => this.loadError.set(humanizeError(err))
    });
  }

  /** The doctor's own accept/decline decision on a booking request. */
  decide(appointment: Appointment, decision: 'acceptance' | 'decline'): void {
    this.appointments.decide(appointment.id, decision).subscribe({
      next: () => {
        this.snackBar.open(
          decision === 'acceptance'
            ? `Accepted — ${appointment.patientName} is confirmed`
            : `Declined — the slot is free again`,
          'OK', { duration: 3500 });
        this.loadRequests();
        if (this.profile()) { this.loadDay(this.profile()!.id); }
      },
      error: err => this.snackBar.open(humanizeError(err), 'OK', { duration: 5000 })
    });
  }

  private loadDay(doctorId: string): void {
    const date = new Date().toISOString().slice(0, 10);
    this.appointments.doctorDay(doctorId, date).subscribe({
      next: a => this.todayList.set(a),
      error: err => this.loadError.set(humanizeError(err))
    });
  }

  nextPatientHint(): string {
    const next = this.todayList().find(a => new Date(a.startAt) > new Date());
    return next ? `Next at ${new Date(next.startAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}`
                : 'No more today';
  }

  complete(appointment: Appointment): void {
    this.appointments.transition(appointment.id, 'completion').subscribe({
      next: () => {
        // "being created", not "created": the chart is opened by
        // medical-record-service consuming AppointmentCompleted, so it does not
        // exist the moment this call returns. That is the eventual consistency
        // the event-driven design buys, surfacing in the UI — so the message
        // says so rather than claiming something that isn't true yet.
        this.snackBar.open('Visit completed — the chart is being created', 'OK',
          { duration: 3500 });
        if (this.profile()) { this.loadDay(this.profile()!.id); }
        // Refresh now and once more shortly after. Two bounded attempts beats a
        // single guessed delay: if the event is still in flight the first read
        // misses it, and the doctor can always reload.
        this.reloadEncounters();
        setTimeout(() => this.reloadEncounters(), 2000);
      },
      error: err => this.snackBar.open(humanizeError(err), 'OK', { duration: 5000 })
    });
  }
}
