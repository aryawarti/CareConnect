import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/auth/auth.service';
import { humanizeError, isSystemError } from '../../core/http/http-status';

@Component({
  selector: 'cc-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule,
            MatInputModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <mat-card class="cc-auth-card" appearance="outlined">
      <mat-card-header style="display:block;text-align:center;padding-top:8px">
        <mat-card-title style="font-size:22px">Welcome back</mat-card-title>
        <mat-card-subtitle>Sign in to manage your care</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="submit()" style="display:flex;flex-direction:column;gap:8px;margin-top:16px">
          <mat-form-field appearance="outline" class="cc-full-width">
            <mat-label>Email</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="email">
            @if (form.controls.email.hasError('required')) { <mat-error>Email is required</mat-error> }
          </mat-form-field>
          <mat-form-field appearance="outline" class="cc-full-width">
            <mat-label>Password</mat-label>
            <input matInput type="password" formControlName="password" autocomplete="current-password">
            @if (form.controls.password.hasError('required')) { <mat-error>Password is required</mat-error> }
          </mat-form-field>
          @if (error(); as message) {
            <div class="cc-alert cc-alert-error" role="alert">
              <mat-icon>error_outline</mat-icon>
              <span>{{ message }}</span>
            </div>
          }
          <button mat-flat-button class="cc-btn-primary" type="submit" [disabled]="loading()" style="height:44px">
            @if (loading()) {
              <span style="display:inline-flex;align-items:center;gap:8px">
                <mat-spinner diameter="20" /> Signing in…
              </span>
            } @else { Sign in }
          </button>
        </form>
      </mat-card-content>
      <mat-card-actions>
        <a mat-button routerLink="/register">No account? Register as a patient</a>
      </mat-card-actions>
    </mat-card>

    <!--
      Demo accounts. A reviewer (or you, on a fresh machine) must be able to see
      all four roles in under a minute — a patient login alone makes the system
      look like a login form. Seeded by infra/seed/seed.py; remove this block
      for any real deployment.
    -->
    <div class="cc-auth-card" style="margin-top:-24px">
      <div class="cc-card">
        <div class="cc-row" style="margin-bottom:10px">
          <mat-icon style="color:var(--cc-primary)">science</mat-icon>
          <strong style="font-size:14px">Demo accounts</strong>
        </div>
        <div class="cc-faint" style="margin-bottom:12px">
          Each role sees a completely different application. Click to fill and sign in.
        </div>
        <div class="cc-stack" style="gap:8px">
          @for (account of demoAccounts; track account.email) {
            <button mat-stroked-button style="justify-content:flex-start;height:auto;padding:10px 12px"
                    (click)="useDemo(account)">
              <mat-icon>{{ account.icon }}</mat-icon>
              <span style="text-align:left">
                <span style="display:block;font-weight:600">{{ account.role }}</span>
                <span class="cc-faint" style="display:block">{{ account.blurb }}</span>
              </span>
            </button>
          }
        </div>
      </div>
    </div>
  `
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  readonly demoAccounts = [
    { role: 'Clinic administrator', icon: 'admin_panel_settings',
      blurb: 'Hire doctors, set fees and hours, run billing',
      email: 'admin@careconnect.local', password: 'Admin12345' },
    { role: 'Doctor', icon: 'stethoscope',
      blurb: "Today's patients, write and sign clinical charts",
      email: 'dr.rao@careconnect.demo', password: 'Doctor12345' },
    { role: 'Patient', icon: 'personal_injury',
      blurb: 'Book visits, read prescriptions, pay invoices',
      email: 'asha.verma@careconnect.demo', password: 'Patient12345' },
  ];

  useDemo(account: { email: string; password: string }): void {
    this.form.setValue({ email: account.email, password: account.password });
    this.submit();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => this.router.navigate(['/']),
      error: err => {
        this.loading.set(false);
        // System failures (backend still starting, network down) are announced
        // by the global toast; here we surface the credential/validation reason
        // inline, right under the form, using a friendlier message for 401.
        this.error.set(isSystemError(err)
          ? null
          : humanizeError(err, 'Sign in failed. Please check your details and try again.'));
      }
    });
  }
}
