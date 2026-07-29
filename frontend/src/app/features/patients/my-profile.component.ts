import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { PatientsService } from '../../core/patients/patients.service';
import { Patient } from '../../core/patients/patient.models';

@Component({
  selector: 'cc-my-profile',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, MatCardModule, MatListModule, MatIconModule,
            MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
            MatSnackBarModule],
  template: `
    <div class="cc-page" style="max-width:640px">
      @if (profile(); as p) {
        <mat-card appearance="outlined">
          <mat-card-header>
            <mat-icon mat-card-avatar>badge</mat-icon>
            <mat-card-title>{{ p.firstName }} {{ p.lastName }}</mat-card-title>
            <mat-card-subtitle>MRN {{ p.patientNumber }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <mat-list>
              <mat-list-item>
                <mat-icon matListItemIcon>cake</mat-icon>
                {{ p.dateOfBirth | date:'longDate' }}
              </mat-list-item>
              <mat-list-item>
                <mat-icon matListItemIcon>call</mat-icon>
                {{ p.phone || 'No phone on file' }}
              </mat-list-item>
              <mat-list-item>
                <mat-icon matListItemIcon>mail</mat-icon>
                {{ p.email || 'No email on file' }}
              </mat-list-item>
              <mat-list-item>
                <mat-icon matListItemIcon>emergency</mat-icon>
                {{ p.emergencyContactName || 'No emergency contact' }}
              </mat-list-item>
            </mat-list>
          </mat-card-content>
        </mat-card>
      } @else if (missing()) {
        <mat-card appearance="outlined">
          <mat-card-header>
            <mat-icon mat-card-avatar>assignment_ind</mat-icon>
            <mat-card-title>Complete your patient profile</mat-card-title>
            <mat-card-subtitle>Needed once before you can book appointments</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <form [formGroup]="form" (ngSubmit)="create()"
                  style="display:grid;grid-template-columns:1fr 1fr;gap:8px 16px;margin-top:16px">
              <mat-form-field appearance="outline">
                <mat-label>First name</mat-label>
                <input matInput formControlName="firstName">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Last name</mat-label>
                <input matInput formControlName="lastName">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Date of birth</mat-label>
                <input matInput type="date" formControlName="dateOfBirth" [max]="today">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Gender</mat-label>
                <mat-select formControlName="gender">
                  <mat-option value="FEMALE">Female</mat-option>
                  <mat-option value="MALE">Male</mat-option>
                  <mat-option value="OTHER">Other</mat-option>
                  <mat-option value="UNDISCLOSED">Prefer not to say</mat-option>
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Phone</mat-label>
                <input matInput formControlName="phone">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Emergency contact name</mat-label>
                <input matInput formControlName="emergencyContactName">
              </mat-form-field>
              <div style="grid-column:1/-1;display:flex;justify-content:flex-end">
                <button mat-flat-button class="cc-btn-primary" type="submit">Create my profile</button>
              </div>
            </form>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `
})
export class MyProfileComponent {
  private readonly service = inject(PatientsService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  readonly today = new Date().toISOString().slice(0, 10);
  readonly profile = signal<Patient | null>(null);
  readonly missing = signal(false);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    dateOfBirth: ['', Validators.required],
    gender: ['FEMALE' as Patient['gender'], Validators.required],
    phone: [''],
    emergencyContactName: ['']
  });

  constructor() {
    this.load();
  }

  create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.service.createMyProfile(this.form.getRawValue()).subscribe({
      next: p => {
        this.snackBar.open(`Welcome, ${p.firstName}! Your MRN is ${p.patientNumber}`, 'OK',
            { duration: 5000 });
        this.missing.set(false);
        this.profile.set(p);
      },
      error: err => this.snackBar.open(err?.error?.detail ?? 'Could not create profile', 'OK',
          { duration: 4000 })
    });
  }

  private load(): void {
    this.service.myProfile().subscribe({
      next: p => this.profile.set(p),
      error: () => this.missing.set(true)
    });
  }
}
