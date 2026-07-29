import { Component, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { PatientDashboardComponent } from './patient-dashboard.component';
import { DoctorDashboardComponent } from './doctor-dashboard.component';
import { StaffDashboardComponent } from './staff-dashboard.component';

/**
 * "Home" is whichever dashboard fits the signed-in role. Staff/admin see the
 * clinic, doctors see their day, patients see their care. Same route, three
 * genuinely different jobs — role-based UX rather than one page with
 * everything hidden behind conditionals.
 */
@Component({
  selector: 'cc-home',
  standalone: true,
  imports: [PatientDashboardComponent, DoctorDashboardComponent, StaffDashboardComponent],
  template: `
    @if (isStaff()) {
      <cc-staff-dashboard />
    } @else if (isDoctor()) {
      <cc-doctor-dashboard />
    } @else {
      <cc-patient-dashboard />
    }
  `
})
export class HomeComponent {
  private readonly auth = inject(AuthService);

  isStaff(): boolean {
    return this.has('STAFF', 'ADMIN');
  }

  isDoctor(): boolean {
    return this.has('DOCTOR');
  }

  private has(...roles: string[]): boolean {
    const user = this.auth.user();
    return !!user && roles.some(r => user.roles.includes(r));
  }
}
