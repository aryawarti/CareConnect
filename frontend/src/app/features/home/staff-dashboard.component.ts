import { Component, computed, inject } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AppointmentsService } from '../../core/appointments/appointments.service';
import { BillingService } from '../../core/billing/billing.service';
import { PatientsService } from '../../core/patients/patients.service';
import { ProvidersService } from '../../core/providers/providers.service';
import { Appointment } from '../../core/appointments/appointment.models';
import { asyncResource } from '../../core/http/async-resource';
import { humanizeError } from '../../core/http/http-status';
import { StatComponent, EmptyStateComponent, DonutComponent, BarChartComponent,
         ErrorPanelComponent } from '../../shared/ui.components';

/** Clinic-wide operational view for staff and admins. */
@Component({
  selector: 'cc-staff-dashboard',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, RouterLink, MatButtonModule, MatIconModule, MatSnackBarModule,
            StatComponent, EmptyStateComponent, DonutComponent, BarChartComponent,
            ErrorPanelComponent],
  template: `
    <div class="cc-page">
      @if (loadError(); as problem) {
        <div style="margin-bottom:18px">
          <cc-error [message]="problem + ' Figures below may be incomplete.'"
                    (retry)="reloadAll()" />
        </div>
      }

      <div class="cc-page-head">
        <div>
          <h1>Clinic today</h1>
          <div class="cc-sub">{{ today | date:'EEEE, MMMM d, y' }}</div>
        </div>
        <span class="cc-spacer"></span>
        <a mat-stroked-button routerLink="/staff"><mat-icon>badge</mat-icon> Staff</a>
        <a mat-flat-button class="cc-btn-primary" routerLink="/queue">
          <mat-icon>groups_2</mat-icon> Live queue
        </a>
      </div>

      <div class="cc-grid cc-grid-4" style="margin-bottom:22px">
        <cc-stat icon="event" [value]="dayAppointments().length" label="Appointments today"
                 [hint]="pending().length + ' awaiting confirmation'" />
        <cc-stat icon="groups" [value]="patientCount()" label="Registered patients" tone="info" />
        <cc-stat icon="pending_actions" [value]="outstandingTotal() | currency:'INR':'symbol':'1.0-0'"
                 label="Outstanding revenue" tone="accent"
                 [hint]="outstanding().length + ' unpaid invoices'" />
        <cc-stat icon="payments" [value]="collectedTotal() | currency:'INR':'symbol':'1.0-0'"
                 label="Collected (recent)" tone="ok" />
      </div>

      @if (applications().length) {
        <div class="cc-card" style="border-left:4px solid var(--cc-warn);margin-bottom:16px">
          <div class="cc-row">
            <mat-icon style="color:var(--cc-warn)">verified_user</mat-icon>
            <div style="flex:1">
              <div style="font-weight:600">
                {{ applications().length }} doctor application{{ applications().length > 1 ? 's' : '' }}
                waiting for verification
              </div>
              <div class="cc-muted" style="font-size:14px">
                They can't be booked by patients until you approve them.
              </div>
            </div>
            <a mat-flat-button class="cc-btn-primary" routerLink="/doctor-approvals">Review</a>
          </div>
        </div>
      }

      <div class="cc-grid cc-grid-2" style="margin-bottom:16px">
        <cc-donut title="Today's appointments by status" centerLabel="today"
                  [data]="statusBreakdown()" />
        <cc-bar-chart title="Invoice value by status" subtitle="Across recent invoices"
                      [data]="revenueBars()" />
      </div>

      <div class="cc-grid cc-grid-2">
        <div class="cc-card">
          <div class="cc-row">
            <h3 style="flex:1">Awaiting confirmation</h3>
            <a mat-button routerLink="/schedule">Open schedule</a>
          </div>
          @if (pending().length) {
            <div class="cc-stack" style="margin-top:12px">
              @for (a of pending().slice(0, 5); track a.id) {
                <div class="cc-row" style="padding:10px 0;border-bottom:1px solid var(--cc-line)">
                  <div style="flex:1">
                    <div style="font-weight:600">{{ a.patientName }}</div>
                    <div class="cc-faint">
                      {{ a.startAt | date:'h:mm a' }} · {{ a.doctorName }}
                    </div>
                  </div>
                  <button mat-flat-button class="cc-btn-primary" (click)="confirm(a)">Confirm</button>
                </div>
              }
            </div>
          } @else {
            <cc-empty icon="task_alt" title="All caught up"
                      text="Every appointment today is confirmed." />
          }
        </div>

        <div class="cc-card">
          <div class="cc-row">
            <h3 style="flex:1">Unpaid invoices</h3>
            <a mat-button routerLink="/billing">Billing</a>
          </div>
          @if (outstanding().length) {
            <div class="cc-stack" style="margin-top:12px">
              @for (i of outstanding().slice(0, 5); track i.id) {
                <div class="cc-row" style="padding:10px 0;border-bottom:1px solid var(--cc-line)">
                  <div style="flex:1">
                    <div style="font-weight:600">{{ i.patientName }}</div>
                    <div class="cc-faint">{{ i.invoiceNumber }} · {{ i.doctorName }}</div>
                  </div>
                  <span class="cc-money">{{ i.amount | currency:'INR':'symbol':'1.0-0' }}</span>
                </div>
              }
            </div>
          } @else {
            <cc-empty icon="receipt_long" title="Nothing outstanding"
                      text="Every issued invoice has been settled." />
          }
        </div>
      </div>
    </div>
  `
})
export class StaffDashboardComponent {
  private readonly appointments = inject(AppointmentsService);
  private readonly billing = inject(BillingService);
  private readonly patients = inject(PatientsService);
  private readonly providers = inject(ProvidersService);
  private readonly snackBar = inject(MatSnackBar);

  readonly today = new Date();

  /**
   * Six independent requests. Each is a resource so a failure is reported rather
   * than silently rendering as a zero — a staff member reading "0 outstanding"
   * because billing-service was unreachable would draw exactly the wrong
   * conclusion about the day's collections.
   *
   * Exposed as computeds with the names the template already used, so the states
   * were added without rewriting the markup.
   */
  private readonly dayResource = asyncResource(() =>
    this.appointments.clinicDay(new Date().toISOString().slice(0, 10)));
  private readonly issuedResource = asyncResource(() =>
    this.billing.byStatus('ISSUED', 0, 50).pipe(map(r => r.data)));
  private readonly paidResource = asyncResource(() =>
    this.billing.byStatus('PAID', 0, 50).pipe(map(r => r.data)));
  private readonly patientCountResource = asyncResource(() =>
    this.patients.search('', 0, 1).pipe(map(r => r.meta.totalElements)));
  private readonly doctorCountResource = asyncResource(() =>
    this.providers.directory('', 0, 1).pipe(map(r => r.meta.totalElements)));
  private readonly applicationsResource = asyncResource(() => this.providers.applications());

  readonly dayAppointments = computed(() => this.dayResource.value() ?? []);
  readonly issued = computed(() => this.issuedResource.value() ?? []);
  readonly paid = computed(() => this.paidResource.value() ?? []);
  readonly patientCount = computed(() => this.patientCountResource.value() ?? 0);
  readonly doctorCount = computed(() => this.doctorCountResource.value() ?? 0);
  readonly applications = computed(() => this.applicationsResource.value() ?? []);

  readonly loading = computed(() => this.dayResource.loading() || this.issuedResource.loading());
  /** First failure across the six, so an incomplete day is never presented as fact. */
  readonly loadError = computed(() =>
    this.dayResource.error() ?? this.issuedResource.error() ?? this.paidResource.error()
    ?? this.patientCountResource.error() ?? this.doctorCountResource.error());

  readonly pending = computed(() =>
    this.dayAppointments().filter(a => a.status === 'REQUESTED'));
  readonly outstanding = computed(() => this.issued());

  readonly outstandingTotal = computed(() =>
    this.issued().reduce((sum, i) => sum + Number(i.amount), 0));
  readonly collectedTotal = computed(() =>
    this.paid().reduce((sum, i) => sum + Number(i.amount), 0));

  readonly statusBreakdown = computed(() => {
    const count = (status: string) =>
      this.dayAppointments().filter(a => a.status === status).length;
    return [
      { label: 'Confirmed', value: count('CONFIRMED'), color: 'var(--cc-ok)' },
      { label: 'Requested', value: count('REQUESTED'), color: 'var(--cc-warn)' },
      { label: 'Completed', value: count('COMPLETED'), color: 'var(--cc-primary)' },
      { label: 'Cancelled', value: count('CANCELLED') + count('NO_SHOW'), color: 'var(--cc-danger)' },
    ];
  });

  readonly revenueBars = computed(() => [
    { label: 'Outstanding', value: Math.round(this.outstandingTotal()) },
    { label: 'Collected', value: Math.round(this.collectedTotal()) },
  ]);

  reloadAll(): void {
    this.dayResource.reload();
    this.issuedResource.reload();
    this.paidResource.reload();
    this.patientCountResource.reload();
    this.doctorCountResource.reload();
    this.applicationsResource.reload();
  }

  confirm(appointment: Appointment): void {
    this.appointments.transition(appointment.id, 'confirmation').subscribe({
      next: () => {
        this.snackBar.open(`Confirmed ${appointment.patientName}`, 'OK', { duration: 3000 });
        this.dayResource.reload();
      },
      error: err => this.snackBar.open(humanizeError(err), 'OK', { duration: 5000 })
    });
  }
}
