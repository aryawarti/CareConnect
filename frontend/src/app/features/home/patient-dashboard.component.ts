import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AppointmentsService } from '../../core/appointments/appointments.service';
import { BillingService } from '../../core/billing/billing.service';
import { RecordsService } from '../../core/records/records.service';
import { PatientsService } from '../../core/patients/patients.service';
import { asyncResource } from '../../core/http/async-resource';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent, StatComponent }
  from '../../shared/ui.components';

/**
 * The patient's landing screen, which aggregates three independent requests.
 *
 * Each card owns its own states rather than the page having one global spinner:
 * a slow invoices call should not hide appointments that already arrived. The
 * stat tiles show an em dash while loading, because rendering "0 upcoming
 * appointments" before the answer arrives states something false.
 */
@Component({
  selector: 'cc-patient-dashboard',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, RouterLink, MatButtonModule, MatIconModule,
            StatComponent, EmptyStateComponent, SkeletonComponent, ErrorPanelComponent],
  template: `
    <div class="cc-page">
      <div class="cc-page-head">
        <div>
          <h1>{{ greeting() }}{{ firstName() ? ', ' + firstName() : '' }}</h1>
          <div class="cc-sub">Here's where things stand with your care.</div>
        </div>
        <span class="cc-spacer"></span>
        <a mat-flat-button class="cc-btn-primary" routerLink="/book" style="height:44px">
          <mat-icon>event</mat-icon> Book appointment
        </a>
      </div>

      @if (needsProfile()) {
        <div class="cc-card" style="border-left:4px solid var(--cc-warn);margin-bottom:20px">
          <div class="cc-row">
            <mat-icon style="color:var(--cc-warn)">info</mat-icon>
            <div style="flex:1">
              <div style="font-weight:600">Complete your patient profile</div>
              <div class="cc-muted" style="font-size:14px">
                We need your details once before you can book an appointment.
              </div>
            </div>
            <a mat-flat-button class="cc-btn-primary" routerLink="/my-profile">Complete now</a>
          </div>
        </div>
      }

      <div class="cc-grid cc-grid-4" style="margin-bottom:22px">
        <cc-stat icon="event_upcoming" label="Upcoming appointments"
                 [value]="appointments.loading() ? '—' : upcoming().length"
                 [hint]="nextVisitHint()" />
        <cc-stat icon="clinical_notes" label="Visit records" tone="info"
                 [value]="records.loading() ? '—' : (records.value()?.length ?? 0)"
                 hint="Notes and prescriptions" />
        <cc-stat icon="account_balance_wallet" label="Outstanding balance" tone="accent"
                 [value]="invoices.loading()
                            ? '—'
                            : (outstanding() | currency:'INR':'symbol':'1.0-0')"
                 [hint]="invoices.loading() ? 'Loading…' : unpaidCount() + ' invoice(s) due'" />
        <cc-stat icon="task_alt" label="Completed visits" tone="ok"
                 [value]="appointments.loading() ? '—' : completedCount()" />
      </div>

      <div class="cc-grid cc-grid-2">
        <div class="cc-card">
          <div class="cc-row">
            <h3 style="flex:1">Next appointments</h3>
            <a mat-button routerLink="/my-appointments">All</a>
          </div>
          @if (appointments.loading()) {
            <cc-skeleton [count]="3" label="Loading appointments…" />
          } @else if (appointments.failed()) {
            <cc-error [message]="appointments.error()!" (retry)="appointments.reload()" />
          } @else if (upcoming().length) {
            <div class="cc-timeline" style="margin-top:14px">
              @for (a of upcoming().slice(0, 4); track a.id) {
                <div class="cc-timeline-item">
                  <div class="cc-row">
                    <div style="flex:1">
                      <div style="font-weight:600">{{ a.doctorName }}</div>
                      <div class="cc-faint">{{ a.startAt | date:'EEE, MMM d · h:mm a' }}</div>
                      @if (a.reason) { <div class="cc-faint">{{ a.reason }}</div> }
                    </div>
                    <span class="cc-pill" [class]="a.status">{{ a.status }}</span>
                  </div>
                </div>
              }
            </div>
          } @else {
            <cc-empty icon="event_available" title="Nothing scheduled"
                      text="Book a slot with a specialist — you'll see it here.">
              <a mat-flat-button class="cc-btn-primary" routerLink="/book">Book appointment</a>
            </cc-empty>
          }
        </div>

        <div class="cc-card">
          <div class="cc-row">
            <h3 style="flex:1">Recent visits</h3>
            <a mat-button routerLink="/my-records">All</a>
          </div>
          @if (records.loading()) {
            <cc-skeleton [count]="3" label="Loading visits…" />
          } @else if (records.failed()) {
            <cc-error [message]="records.error()!" (retry)="records.reload()" />
          } @else if (records.value()?.length) {
            <div class="cc-stack" style="margin-top:14px">
              @for (r of records.value()!.slice(0, 4); track r.id) {
                <div class="cc-row cc-list-row">
                  <div style="flex:1">
                    <div style="font-weight:600">{{ r.doctorName }}</div>
                    <div class="cc-faint">
                      {{ r.occurredAt | date:'mediumDate' }}
                      @if (r.chiefComplaint) { · {{ r.chiefComplaint }} }
                    </div>
                  </div>
                  <span class="cc-pill" [class]="r.status">{{ r.status }}</span>
                </div>
              }
            </div>
          } @else {
            <cc-empty icon="clinical_notes" title="No visit records yet"
                      text="A record appears automatically after a completed appointment." />
          }
        </div>
      </div>

      @if (unpaid().length) {
        <div class="cc-card" style="margin-top:16px">
          <div class="cc-row">
            <h3 style="flex:1">Invoices due</h3>
            <a mat-button routerLink="/my-invoices">All invoices</a>
          </div>
          <div class="cc-stack" style="margin-top:12px">
            @for (i of unpaid().slice(0, 3); track i.id) {
              <div class="cc-row cc-list-row">
                <div style="flex:1">
                  <div style="font-weight:600">{{ i.invoiceNumber }}</div>
                  <div class="cc-faint">
                    {{ i.doctorName }} · {{ i.issuedAt | date:'mediumDate' }}
                  </div>
                </div>
                <span class="cc-money">{{ i.amount | currency:'INR':'symbol':'1.2-2' }}</span>
                <a mat-flat-button class="cc-btn-primary" routerLink="/my-invoices">Pay</a>
              </div>
            }
          </div>
        </div>
      }
    </div>
  `
})
export class PatientDashboardComponent {
  private readonly appointmentsApi = inject(AppointmentsService);
  private readonly billing = inject(BillingService);
  private readonly recordsApi = inject(RecordsService);
  private readonly patients = inject(PatientsService);

  readonly appointments = asyncResource(() =>
    this.appointmentsApi.mine(0, 50).pipe(map(r => r.data)));
  readonly invoices = asyncResource(() =>
    this.billing.myInvoices(0, 50).pipe(map(r => r.data)));
  readonly records = asyncResource(() =>
    this.recordsApi.myHistory(0, 20).pipe(map(r => r.data)));

  readonly needsProfile = signal(false);
  readonly firstName = signal('');

  readonly upcoming = computed(() => (this.appointments.value() ?? [])
    .filter(a => ['REQUESTED', 'CONFIRMED'].includes(a.status)
                 && new Date(a.startAt) > new Date())
    .sort((a, b) => a.startAt.localeCompare(b.startAt)));

  readonly completedCount = computed(() =>
    (this.appointments.value() ?? []).filter(a => a.status === 'COMPLETED').length);

  readonly unpaid = computed(() =>
    (this.invoices.value() ?? []).filter(i => i.status === 'ISSUED'));
  readonly unpaidCount = computed(() => this.unpaid().length);
  readonly outstanding = computed(() =>
    this.unpaid().reduce((sum, i) => sum + Number(i.amount), 0));

  constructor() {
    this.patients.myProfile().subscribe({
      next: p => this.firstName.set(p.firstName),
      error: (err: HttpErrorResponse) => {
        // Only a 404 means "no profile yet". Treating every failure as one — as
        // this did — nags a patient to complete a profile they already have
        // whenever patient-service is briefly unavailable.
        if (err.status === 404) {
          this.needsProfile.set(true);
        }
      }
    });
  }

  greeting(): string {
    const hour = new Date().getHours();
    return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
  }

  nextVisitHint(): string {
    if (this.appointments.loading()) { return 'Loading…'; }
    const next = this.upcoming()[0];
    if (!next) { return 'Nothing scheduled'; }
    const days = Math.round((new Date(next.startAt).getTime() - Date.now()) / 86400000);
    return days <= 0 ? 'Today' : days === 1 ? 'Tomorrow' : `In ${days} days`;
  }
}
