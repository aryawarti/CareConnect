import { Component, inject, signal } from '@angular/core';
import { DatePipe, CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AppointmentsService } from '../../core/appointments/appointments.service';
import { Appointment } from '../../core/appointments/appointment.models';
import { asyncResource } from '../../core/http/async-resource';
import { humanizeError } from '../../core/http/http-status';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/ui.components';

@Component({
  selector: 'cc-my-appointments',
  standalone: true,
  imports: [DatePipe, CurrencyPipe, RouterLink, MatCardModule, MatButtonModule,
            MatIconModule, MatSnackBarModule, SkeletonComponent, ErrorPanelComponent,
            EmptyStateComponent],
  template: `
    <div class="cc-page cc-narrow">
      <div class="cc-page-head">
        <div><h1>My appointments</h1><div class="cc-sub">Upcoming and past visits</div></div>
        <span class="cc-spacer"></span>
        <a mat-flat-button class="cc-btn-primary" routerLink="/book">
          <mat-icon>event</mat-icon> Book new
        </a>
      </div>

      @if (appointments.loading()) {
        <cc-skeleton [count]="3" variant="card" label="Loading your appointments…" />
      } @else if (appointments.failed()) {
        <cc-error [message]="appointments.error()!" (retry)="appointments.reload()" />
      } @else if (appointments.value()?.length) {
        <div [class.cc-stale]="appointments.refreshing()">
          @for (a of appointments.value(); track a.id) {
            <mat-card appearance="outlined" style="margin-bottom:12px">
              <mat-card-content class="cc-row" style="padding-top:16px">
                <div style="flex:1">
                  <strong>{{ a.doctorName }}</strong><br>
                  {{ a.startAt | date:'EEE, MMM d, y · h:mm a' }}
                  — {{ a.feeSnapshot | currency:'INR':'symbol':'1.0-0' }}
                  @if (a.reason) { <br><span class="cc-muted">{{ a.reason }}</span> }
                </div>
                <span class="cc-pill" [class]="a.status">{{ a.status }}</span>
                @if (a.status === 'REQUESTED' || a.status === 'CONFIRMED') {
                  <button mat-stroked-button [disabled]="cancelling() === a.id"
                          (click)="cancel(a)">
                    {{ cancelling() === a.id ? 'Cancelling…' : 'Cancel' }}
                  </button>
                }
              </mat-card-content>
            </mat-card>
          }
        </div>
      } @else {
        <cc-empty icon="event_busy" title="No appointments yet"
                  text="When you book a visit it will appear here, with its status and fee.">
          <a mat-flat-button class="cc-btn-primary" routerLink="/book">
            <mat-icon>event</mat-icon> Book your first appointment
          </a>
        </cc-empty>
      }
    </div>
  `
})
export class MyAppointmentsComponent {
  private readonly service = inject(AppointmentsService);
  private readonly snackBar = inject(MatSnackBar);

  readonly appointments = asyncResource(() => this.service.mine().pipe(map(r => r.data)));

  /** Id of the row being cancelled, so one button can't be double-submitted. */
  readonly cancelling = signal<string | null>(null);

  cancel(a: Appointment): void {
    if (this.cancelling()) {
      return;
    }
    this.cancelling.set(a.id);
    this.service.transition(a.id, 'cancellation').subscribe({
      next: () => {
        this.cancelling.set(null);
        this.snackBar.open('Appointment cancelled', 'OK', { duration: 3000 });
        this.appointments.reload();
      },
      error: err => {
        this.cancelling.set(null);
        // Cancelling too close to the start is a rule, not a glitch — the
        // server's message says which, so show it rather than "failed".
        this.snackBar.open(humanizeError(err), 'OK', { duration: 5000 });
      }
    });
  }
}
