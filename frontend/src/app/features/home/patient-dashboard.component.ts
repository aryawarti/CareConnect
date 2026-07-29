import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/auth/auth.service';
import { AppointmentsService } from '../../core/appointments/appointments.service';
import { BillingService } from '../../core/billing/billing.service';
import { RecordsService } from '../../core/records/records.service';
import { PatientsService } from '../../core/patients/patients.service';
import { Appointment } from '../../core/appointments/appointment.models';
import { Invoice } from '../../core/billing/billing.models';
import { Encounter } from '../../core/records/record.models';
import { StatComponent, EmptyStateComponent } from '../../shared/ui.components';

@Component({
  selector: 'cc-patient-dashboard',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, RouterLink, MatButtonModule, MatIconModule,
            StatComponent, EmptyStateComponent],
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
        <cc-stat icon="event_upcoming" [value]="upcoming().length"
                 label="Upcoming appointments"
                 [hint]="nextVisitHint()" />
        <cc-stat icon="clinical_notes" [value]="records().length" label="Visit records" tone="info"
                 hint="Notes and prescriptions" />
        <cc-stat icon="account_balance_wallet"
                 [value]="outstanding() | currency:'INR':'symbol':'1.0-0'"
                 label="Outstanding balance" tone="accent"
                 [hint]="unpaidCount() + ' invoice(s) due'" />
        <cc-stat icon="task_alt" [value]="completedCount()" label="Completed visits" tone="ok" />
      </div>

      <div class="cc-grid cc-grid-2">
        <div class="cc-card">
          <div class="cc-row">
            <h3 style="flex:1">Next appointments</h3>
            <a mat-button routerLink="/my-appointments">All</a>
          </div>
          @if (upcoming().length) {
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
          @if (records().length) {
            <div class="cc-stack" style="margin-top:14px">
              @for (r of records().slice(0, 4); track r.id) {
                <div class="cc-row" style="padding:10px 0;border-bottom:1px solid var(--cc-line)">
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
              <div class="cc-row" style="padding:10px 0;border-bottom:1px solid var(--cc-line)">
                <div style="flex:1">
                  <div style="font-weight:600">{{ i.invoiceNumber }}</div>
                  <div class="cc-faint">{{ i.doctorName }} · {{ i.issuedAt | date:'mediumDate' }}</div>
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
  private readonly auth = inject(AuthService);
  private readonly appointments = inject(AppointmentsService);
  private readonly billing = inject(BillingService);
  private readonly recordsApi = inject(RecordsService);
  private readonly patients = inject(PatientsService);

  readonly all = signal<Appointment[]>([]);
  readonly invoices = signal<Invoice[]>([]);
  readonly records = signal<Encounter[]>([]);
  readonly needsProfile = signal(false);
  readonly firstName = signal('');

  readonly upcoming = computed(() => this.all()
    .filter(a => ['REQUESTED', 'CONFIRMED'].includes(a.status) && new Date(a.startAt) > new Date())
    .sort((a, b) => a.startAt.localeCompare(b.startAt)));

  readonly completedCount = computed(() =>
    this.all().filter(a => a.status === 'COMPLETED').length);

  readonly unpaid = computed(() => this.invoices().filter(i => i.status === 'ISSUED'));
  readonly unpaidCount = computed(() => this.unpaid().length);
  readonly outstanding = computed(() =>
    this.unpaid().reduce((sum, i) => sum + Number(i.amount), 0));

  constructor() {
    this.patients.myProfile().subscribe({
      next: p => this.firstName.set(p.firstName),
      error: () => this.needsProfile.set(true)
    });
    this.appointments.mine(0, 50).subscribe(r => this.all.set(r.data));
    this.billing.myInvoices(0, 50).subscribe(r => this.invoices.set(r.data));
    this.recordsApi.myHistory(0, 20).subscribe(r => this.records.set(r.data));
  }

  greeting(): string {
    const hour = new Date().getHours();
    return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
  }

  nextVisitHint(): string {
    const next = this.upcoming()[0];
    if (!next) { return 'Nothing scheduled'; }
    const days = Math.round((new Date(next.startAt).getTime() - Date.now()) / 86400000);
    return days <= 0 ? 'Today' : days === 1 ? 'Tomorrow' : `In ${days} days`;
  }
}
