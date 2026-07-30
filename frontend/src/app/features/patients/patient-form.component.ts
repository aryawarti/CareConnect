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
import { humanizeError } from '../../core/http/http-status';

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
            <!-- Every required field states what is wrong, next to the field.
                 The validators existed already but nothing rendered their state,
                 so a form that refused to submit gave no reason why. -->
            <mat-form-field appearance="outline">
              <mat-label>First name</mat-label>
              <input matInput formControlName="firstName" required>
              @if (invalid('firstName')) {
                <mat-error>A first name is required</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Last name</mat-label>
              <input matInput formControlName="lastName" required>
              @if (invalid('lastName')) {
                <mat-error>A last name is required</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Date of birth</mat-label>
              <input matInput [matDatepicker]="dp" formControlName="dateOfBirth"
                     [max]="todayDate" required>
              <mat-datepicker-toggle matIconSuffix [for]="dp" />
              <mat-datepicker #dp />
              <mat-hint>Cannot be in the future</mat-hint>
              @if (invalid('dateOfBirth')) {
                <mat-error>A date of birth is required</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Gender</mat-label>
              <mat-select formControlName="gender" required>
                <mat-option value="FEMALE">Female</mat-option>
                <mat-option value="MALE">Male</mat-option>
                <mat-option value="OTHER">Other</mat-option>
                <mat-option value="UNDISCLOSED">Prefer not to say</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Phone</mat-label>
              <input matInput formControlName="phone" inputmode="tel">
              @if (invalid('phone')) {
                <mat-error>Enter a 10–15 digit phone number</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email">
              @if (invalid('email')) {
                <mat-error>That doesn't look like an email address</mat-error>
              }
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
              <button mat-flat-button class="cc-btn-primary" type="submit"
                      [disabled]="saving()" [attr.aria-busy]="saving()">
                {{ saving() ? 'Saving…' : 'Save' }}
              </button>
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

  /** Datepicker upper bound — nobody is born tomorrow. */
  readonly todayDate = new Date();

  readonly form = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    dateOfBirth: ['', Validators.required],
    gender: ['FEMALE' as Patient['gender'], Validators.required],
    // Loose on purpose: this is a clinic that phones people, and rejecting a
    // valid international format to satisfy a regex costs more than it saves.
    phone: ['', Validators.pattern(/^[+]?[\d\s()-]{10,15}$/)],
    email: ['', Validators.email],
    emergencyContactName: [''],
    emergencyContactPhone: ['']
  });

  /**
   * Show a field's error only once the user has engaged with it — flagging an
   * untouched form red on arrival is hostile, and flagging nothing on submit is
   * useless. `save()` marks everything touched, so a failed submit lights up
   * exactly the fields that need attention.
   */
  invalid(field: string): boolean {
    const control = this.form.get(field);
    return !!control && control.invalid && (control.touched || control.dirty);
  }

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
      // Lights up every offending field at once, which is what the invalid()
      // helper above reads.
      this.form.markAllAsTouched();
      return;
    }
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const body = { ...value, dateOfBirth: new Date(value.dateOfBirth).toISOString().slice(0, 10) };
    const id = this.id();
    const call = id ? this.service.update(id, body) : this.service.create(body);
    call.subscribe({
      next: p => {
        this.saving.set(false);
        this.snackBar.open(`Saved ${p.firstName} ${p.lastName} (${p.patientNumber})`, 'OK',
            { duration: 3000 });
        this.back();
      },
      error: err => {
        this.saving.set(false);
        this.snackBar.open(humanizeError(err), 'OK', { duration: 5000 });
      }
    });
  }

  back(): void {
    this.router.navigate(['/patients']);
  }
}
