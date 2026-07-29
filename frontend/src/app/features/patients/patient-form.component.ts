import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { PatientsService } from '../../core/patients/patients.service';
import { Patient } from '../../core/patients/patient.models';

@Component({
  selector: 'cc-patient-form',
  standalone: true,
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatDatepickerModule, MatNativeDateModule, MatButtonModule,
            MatSnackBarModule],
  template: `
    <div class="cc-page" style="max-width:640px">
      <mat-card appearance="outlined">
        <mat-card-header>
          <mat-card-title>{{ id() ? 'Edit patient' : 'Register new patient' }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="save()"
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
              <input matInput [matDatepicker]="dp" formControlName="dateOfBirth">
              <mat-datepicker-toggle matIconSuffix [for]="dp" />
              <mat-datepicker #dp />
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
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email">
            </mat-form-field>
            <mat-form-field appearance="outline" style="grid-column:1/-1">
              <mat-label>Emergency contact name</mat-label>
              <input matInput formControlName="emergencyContactName">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Emergency contact phone</mat-label>
              <input matInput formControlName="emergencyContactPhone">
            </mat-form-field>
            <div style="grid-column:1/-1;display:flex;gap:8px;justify-content:flex-end">
              <button mat-button type="button" (click)="back()">Cancel</button>
              <button mat-flat-button class="cc-btn-primary" type="submit">Save</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `
})
export class PatientFormComponent {
  /** Route param via component input binding (withComponentInputBinding). */
  readonly id = input<string | undefined>();

  private readonly fb = inject(FormBuilder);
  private readonly service = inject(PatientsService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  readonly saving = signal(false);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    dateOfBirth: ['', Validators.required],
    gender: ['FEMALE' as Patient['gender'], Validators.required],
    phone: [''],
    email: ['', Validators.email],
    emergencyContactName: [''],
    emergencyContactPhone: ['']
  });

  ngOnInit(): void {
    const id = this.id();
    if (id) {
      this.service.get(id).subscribe(p => this.form.patchValue({
        firstName: p.firstName, lastName: p.lastName, dateOfBirth: p.dateOfBirth,
        gender: p.gender, phone: p.phone ?? '', email: p.email ?? '',
        emergencyContactName: p.emergencyContactName ?? '',
        emergencyContactPhone: p.emergencyContactPhone ?? ''
      }));
    }
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const body = { ...value, dateOfBirth: new Date(value.dateOfBirth).toISOString().slice(0, 10) };
    const id = this.id();
    const call = id ? this.service.update(id, body) : this.service.create(body);
    call.subscribe({
      next: p => {
        this.snackBar.open(`Saved ${p.firstName} ${p.lastName} (${p.patientNumber})`, 'OK',
            { duration: 3000 });
        this.back();
      },
      error: err => this.snackBar.open(
          err?.error?.detail ?? 'Save failed — check the form', 'OK', { duration: 4000 })
    });
  }

  back(): void {
    this.router.navigate(['/patients']);
  }
}
