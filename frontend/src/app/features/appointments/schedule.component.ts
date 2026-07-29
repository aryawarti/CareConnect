import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProvidersService } from '../../core/providers/providers.service';
import { AppointmentsService } from '../../core/appointments/appointments.service';
import { AuthService } from '../../core/auth/auth.service';
import { Doctor } from '../../core/providers/provider.models';
import { Appointment } from '../../core/appointments/appointment.models';

/** Day schedule for staff (any doctor) and doctors (defaults to themselves). */
@Component({
  selector: 'cc-schedule',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatTableModule, MatButtonModule, MatChipsModule, MatSnackBarModule],
  template: `
    <div class="cc-page">
      <div class="cc-page-head"><div><h1>Day schedule</h1><div class="cc-sub">Confirm, complete, or cancel visits</div></div></div>
      <div style="display:flex;gap:16px;flex-wrap:wrap">
        <mat-form-field appearance="outline" style="min-width:280px">
          <mat-label>Doctor</mat-label>
          <mat-select [formControl]="doctorCtl">
            @for (d of doctors(); track d.id) {
              <mat-option [value]="d.id">Dr. {{ d.firstName }} {{ d.lastName }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Date</mat-label>
          <input matInput type="date" [formControl]="dateCtl">
        </mat-form-field>
      </div>

      <div class="cc-table-wrap">
      <table mat-table [dataSource]="appointments()" style="width:100%">
        <ng-container matColumnDef="time">
          <th mat-header-cell *matHeaderCellDef>Time</th>
          <td mat-cell *matCellDef="let a">{{ a.startAt | date:'h:mm a' }}</td>
        </ng-container>
        <ng-container matColumnDef="patient">
          <th mat-header-cell *matHeaderCellDef>Patient</th>
          <td mat-cell *matCellDef="let a">{{ a.patientName }}</td>
        </ng-container>
        <ng-container matColumnDef="reason">
          <th mat-header-cell *matHeaderCellDef>Reason</th>
          <td mat-cell *matCellDef="let a">{{ a.reason ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let a"><span class="cc-pill" [class]="a.status">{{ a.status }}</span></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let a">
            @if (a.status === 'REQUESTED' && isStaff) {
              <button mat-button (click)="act(a, 'confirmation')">Confirm</button>
            }
            @if (a.status === 'CONFIRMED') {
              <button mat-button (click)="act(a, 'completion')">Complete</button>
              <button mat-button (click)="act(a, 'no-show')">No-show</button>
            }
            @if ((a.status === 'REQUESTED' || a.status === 'CONFIRMED') && isStaff) {
              <button mat-button color="warn" (click)="act(a, 'cancellation')">Cancel</button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      </div>
      @if (!appointments().length) {
        <p style="color:#666;margin-top:16px">No appointments for this day.</p>
      }
    </div>
  `
})
export class ScheduleComponent {
  private readonly providers = inject(ProvidersService);
  private readonly service = inject(AppointmentsService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  readonly columns = ['time', 'patient', 'reason', 'status', 'actions'];
  readonly doctors = signal<Doctor[]>([]);
  readonly appointments = signal<Appointment[]>([]);
  readonly isStaff = ['STAFF', 'ADMIN'].some(r => this.auth.user()?.roles.includes(r));

  readonly doctorCtl = new FormControl('', { nonNullable: true });
  readonly dateCtl = new FormControl(new Date().toISOString().slice(0, 10), { nonNullable: true });

  constructor() {
    this.providers.directory('', 0, 100).subscribe(r => this.doctors.set(r.data));
    this.doctorCtl.valueChanges.subscribe(() => this.reload());
    this.dateCtl.valueChanges.subscribe(() => this.reload());
  }

  act(a: Appointment, action: 'confirmation' | 'completion' | 'no-show' | 'cancellation'): void {
    this.service.transition(a.id, action).subscribe({
      next: () => this.reload(),
      error: err => this.snackBar.open(err?.error?.detail ?? 'Action failed', 'OK', { duration: 4000 })
    });
  }

  private reload(): void {
    if (this.doctorCtl.value && this.dateCtl.value) {
      this.service.doctorDay(this.doctorCtl.value, this.dateCtl.value)
        .subscribe(a => this.appointments.set(a));
    }
  }
}
