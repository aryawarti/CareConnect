import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProvidersService } from '../../core/providers/providers.service';
import { AppointmentsService } from '../../core/appointments/appointments.service';
import { Doctor } from '../../core/providers/provider.models';
import { FreeSlot } from '../../core/appointments/appointment.models';

@Component({
  selector: 'cc-book',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatButtonModule, MatChipsModule, MatSnackBarModule],
  template: `
    <div class="cc-page" style="max-width:720px">
      <div class="cc-page-head"><div><h1>Book an appointment</h1><div class="cc-sub">Pick a doctor, a date, and a free slot</div></div></div>
      <mat-card appearance="outlined">
        <mat-card-content style="display:flex;flex-direction:column;gap:8px;padding-top:16px">
          <mat-form-field appearance="outline">
            <mat-label>Doctor</mat-label>
            <mat-select [formControl]="doctorCtl">
              @for (d of doctors(); track d.id) {
                <mat-option [value]="d.id">
                  Dr. {{ d.firstName }} {{ d.lastName }} — {{ d.specialty }}
                </mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Date</mat-label>
            <input matInput type="date" [formControl]="dateCtl" [min]="today">
          </mat-form-field>

          @if (slots(); as list) {
            @if (list.length) {
              <p>Available slots:</p>
              <mat-chip-listbox [formControl]="slotCtl">
                @for (s of list; track s.startAt) {
                  <mat-chip-option [value]="s.startAt">
                    {{ s.startAt | date:'shortTime' }}
                  </mat-chip-option>
                }
              </mat-chip-listbox>
            } @else if (doctorCtl.value && dateCtl.value) {
              <p style="color:#666">No free slots on this date — try another day.</p>
            }
          }

          <mat-form-field appearance="outline">
            <mat-label>Reason (optional)</mat-label>
            <input matInput [formControl]="reasonCtl" maxlength="500">
          </mat-form-field>

          <button mat-flat-button class="cc-btn-primary" [disabled]="!slotCtl.value" (click)="book()" style="height:44px">
            Book appointment
          </button>
        </mat-card-content>
      </mat-card>
    </div>
  `
})
export class BookComponent {
  private readonly providers = inject(ProvidersService);
  private readonly appointments = inject(AppointmentsService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  readonly today = new Date().toISOString().slice(0, 10);
  readonly doctors = signal<Doctor[]>([]);
  readonly slots = signal<FreeSlot[] | null>(null);

  readonly doctorCtl = new FormControl('', { nonNullable: true });
  readonly dateCtl = new FormControl(this.today, { nonNullable: true });
  readonly slotCtl = new FormControl<string | null>(null);
  readonly reasonCtl = new FormControl('', { nonNullable: true });

  constructor() {
    this.providers.directory('', 0, 100).subscribe(r => this.doctors.set(r.data));
    this.doctorCtl.valueChanges.subscribe(() => this.loadSlots());
    this.dateCtl.valueChanges.subscribe(() => this.loadSlots());
  }

  private loadSlots(): void {
    this.slotCtl.setValue(null);
    if (this.doctorCtl.value && this.dateCtl.value) {
      this.appointments.available(this.doctorCtl.value, this.dateCtl.value)
        .subscribe(s => this.slots.set(s));
    }
  }

  book(): void {
    this.appointments.book(this.doctorCtl.value, this.slotCtl.value!, this.reasonCtl.value)
      .subscribe({
        next: a => {
          this.snackBar.open(`Booked with ${a.doctorName} — awaiting confirmation`, 'OK',
              { duration: 4000 });
          this.router.navigate(['/my-appointments']);
        },
        error: err => {
          this.snackBar.open(err?.error?.detail ?? 'Booking failed', 'OK', { duration: 4000 });
          this.loadSlots();   // slot may have just been taken (409) — refresh
        }
      });
  }
}
