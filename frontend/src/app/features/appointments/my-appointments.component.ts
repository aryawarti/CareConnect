import { Component, inject, signal } from '@angular/core';
import { DatePipe, CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AppointmentsService } from '../../core/appointments/appointments.service';
import { Appointment } from '../../core/appointments/appointment.models';

@Component({
  selector: 'cc-my-appointments',
  standalone: true,
  imports: [DatePipe, CurrencyPipe, RouterLink, MatCardModule, MatButtonModule,
            MatChipsModule, MatIconModule, MatSnackBarModule],
  template: `
    <div class="cc-page" style="max-width:720px">
      <div class="cc-page-head">
        <div><h1>My appointments</h1><div class="cc-sub">Upcoming and past visits</div></div><span class="cc-spacer"></span>
        <a mat-flat-button class="cc-btn-primary" routerLink="/book">
          <mat-icon>event</mat-icon> Book new
        </a>
      </div>
      @for (a of appointments(); track a.id) {
        <mat-card appearance="outlined" style="margin-bottom:12px">
          <mat-card-content style="display:flex;align-items:center;gap:16px;padding-top:16px">
            <div style="flex:1">
              <strong>{{ a.doctorName }}</strong><br>
              {{ a.startAt | date:'EEE, MMM d, y · h:mm a' }}
              — {{ a.feeSnapshot | currency:'INR':'symbol':'1.0-0' }}
              @if (a.reason) { <br><span style="color:#666">{{ a.reason }}</span> }
            </div>
            <span class="cc-pill" [class]="a.status">{{ a.status }}</span>
            @if (a.status === 'REQUESTED' || a.status === 'CONFIRMED') {
              <button mat-stroked-button color="warn" (click)="cancel(a)">Cancel</button>
            }
          </mat-card-content>
        </mat-card>
      } @empty {
        <p>No appointments yet — book your first one.</p>
      }
    </div>
  `
})
export class MyAppointmentsComponent {
  private readonly service = inject(AppointmentsService);
  private readonly snackBar = inject(MatSnackBar);

  readonly appointments = signal<Appointment[]>([]);

  constructor() {
    this.reload();
  }

  cancel(a: Appointment): void {
    this.service.transition(a.id, 'cancellation').subscribe({
      next: () => { this.snackBar.open('Appointment cancelled', 'OK', { duration: 3000 }); this.reload(); },
      error: err => this.snackBar.open(err?.error?.detail ?? 'Cancellation failed', 'OK',
          { duration: 4000 })
    });
  }

  private reload(): void {
    this.service.mine().subscribe(r => this.appointments.set(r.data));
  }
}
