import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
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
import { StatComponent, EmptyStateComponent } from '../../shared/ui.components';

/** A doctor's working day: who's coming, and which charts still need signing. */
@Component({
  selector: 'cc-doctor-dashboard',
  standalone: true,
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, MatSnackBarModule,
            StatComponent, EmptyStateComponent],
  template: `
    <div class="cc-page">
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
        <a mat-stroked-button routerLink="/schedule"><mat-icon>calendar_month</mat-icon> Schedule</a>
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
  readonly openCharts = computed(() => this.encounters().filter(e => e.status === 'OPEN'));
  readonly signedCount = computed(() =>
    this.encounters().filter(e => e.status !== 'OPEN').length);

  constructor() {
    this.providers.me().subscribe({
      next: doctor => {
        this.profile.set(doctor);
        this.loadDay(doctor.id);
      },
      error: () => this.needsApplication.set(true)
    });
    this.recordsApi.doctorEncounters(0, 50).subscribe(r => this.encounters.set(r.data));
    this.loadRequests();
  }

  private loadRequests(): void {
    this.appointments.doctorRequests().subscribe({
      next: list => this.requests.set(list),
      error: () => this.requests.set([])
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
      error: err => this.snackBar.open(err?.error?.detail ?? 'Action failed', 'OK',
        { duration: 4000 })
    });
  }

  private loadDay(doctorId: string): void {
    const date = new Date().toISOString().slice(0, 10);
    this.appointments.doctorDay(doctorId, date).subscribe(a => this.todayList.set(a));
  }

  nextPatientHint(): string {
    const next = this.todayList().find(a => new Date(a.startAt) > new Date());
    return next ? `Next at ${new Date(next.startAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}`
                : 'No more today';
  }

  complete(appointment: Appointment): void {
    this.appointments.transition(appointment.id, 'completion').subscribe({
      next: () => {
        this.snackBar.open('Visit completed — chart created', 'OK', { duration: 3000 });
        if (this.profile()) { this.loadDay(this.profile()!.id); }
        setTimeout(() =>
          this.recordsApi.doctorEncounters(0, 50).subscribe(r => this.encounters.set(r.data)), 1500);
      },
      error: err => this.snackBar.open(err?.error?.detail ?? 'Failed', 'OK', { duration: 4000 })
    });
  }
}
