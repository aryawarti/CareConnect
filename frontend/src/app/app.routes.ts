import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';

export const routes: Routes = [
  {
    path: 'welcome',
    loadComponent: () => import('./features/home/welcome.component').then(m => m.WelcomeComponent)
  },
  { path: 'login', loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent) },
  { path: 'register', loadComponent: () => import('./features/auth/register.component').then(m => m.RegisterComponent) },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/home/home.component').then(m => m.HomeComponent)
  },
  {
    path: 'patients',
    canActivate: [authGuard, roleGuard('STAFF', 'ADMIN', 'DOCTOR')],
    loadComponent: () => import('./features/patients/patient-list.component').then(m => m.PatientListComponent)
  },
  {
    path: 'patients/new',
    canActivate: [authGuard, roleGuard('STAFF', 'ADMIN')],
    loadComponent: () => import('./features/patients/patient-form.component').then(m => m.PatientFormComponent)
  },
  {
    path: 'patients/:id',
    canActivate: [authGuard, roleGuard('STAFF', 'ADMIN')],
    loadComponent: () => import('./features/patients/patient-form.component').then(m => m.PatientFormComponent)
  },
  {
    path: 'doctors',
    canActivate: [authGuard],
    loadComponent: () => import('./features/providers/directory.component').then(m => m.DirectoryComponent)
  },
  {
    path: 'admin/doctors',
    canActivate: [authGuard, roleGuard('STAFF', 'ADMIN')],
    loadComponent: () => import('./features/providers/doctor-admin.component').then(m => m.DoctorAdminComponent)
  },
  {
    path: 'book',
    canActivate: [authGuard, roleGuard('PATIENT')],
    loadComponent: () => import('./features/appointments/book.component').then(m => m.BookComponent)
  },
  {
    path: 'my-appointments',
    canActivate: [authGuard, roleGuard('PATIENT')],
    loadComponent: () => import('./features/appointments/my-appointments.component').then(m => m.MyAppointmentsComponent)
  },
  {
    path: 'schedule',
    canActivate: [authGuard, roleGuard('STAFF', 'ADMIN', 'DOCTOR')],
    loadComponent: () => import('./features/appointments/schedule.component').then(m => m.ScheduleComponent)
  },
  {
    path: 'my-records',
    canActivate: [authGuard, roleGuard('PATIENT')],
    loadComponent: () => import('./features/records/my-records.component').then(m => m.MyRecordsComponent)
  },
  {
    // "Who has seen my records" — the patient's own view of the access trail.
    path: 'my-record-access',
    canActivate: [authGuard, roleGuard('PATIENT')],
    loadComponent: () => import('./features/records/record-access.component').then(m => m.RecordAccessComponent)
  },
  {
    // The audit view of the same trail, for one patient.
    path: 'patients/:patientId/access',
    canActivate: [authGuard, roleGuard('STAFF', 'ADMIN')],
    loadComponent: () => import('./features/records/record-access.component').then(m => m.RecordAccessComponent)
  },
  {
    path: 'records',
    canActivate: [authGuard, roleGuard('DOCTOR')],
    loadComponent: () => import('./features/records/encounter-editor.component').then(m => m.EncounterEditorComponent)
  },
  {
    path: 'my-invoices',
    canActivate: [authGuard, roleGuard('PATIENT')],
    loadComponent: () => import('./features/billing/my-invoices.component').then(m => m.MyInvoicesComponent)
  },
  {
    path: 'billing',
    canActivate: [authGuard, roleGuard('STAFF', 'ADMIN')],
    loadComponent: () => import('./features/billing/billing-admin.component').then(m => m.BillingAdminComponent)
  },
  {
    // Public kiosk screen: /board/<doctorId> on a lobby TV, no login.
    path: 'board/:doctorId',
    loadComponent: () => import('./features/queue/waiting-board.component').then(m => m.WaitingBoardComponent)
  },
  {
    path: 'queue',
    canActivate: [authGuard, roleGuard('DOCTOR', 'STAFF', 'ADMIN')],
    loadComponent: () => import('./features/queue/doctor-console.component').then(m => m.DoctorConsoleComponent)
  },
  {
    path: 'my-queue',
    canActivate: [authGuard, roleGuard('PATIENT')],
    loadComponent: () => import('./features/queue/my-queue.component').then(m => m.MyQueueComponent)
  },
  {
    path: 'doctor-application',
    canActivate: [authGuard, roleGuard('DOCTOR')],
    loadComponent: () => import('./features/providers/doctor-application.component').then(m => m.DoctorApplicationComponent)
  },
  {
    // A doctor publishing their own consulting hours. Without this the only way
    // to set a schedule was through the administrator's doctor list, so every
    // doctor hired through the application flow was unbookable.
    path: 'my-schedule',
    canActivate: [authGuard, roleGuard('DOCTOR')],
    loadComponent: () => import('./features/providers/my-schedule.component').then(m => m.MyScheduleComponent)
  },
  {
    // Browse by department -> doctors in it -> one doctor's profile -> book.
    path: 'departments',
    canActivate: [authGuard],
    loadComponent: () => import('./features/providers/departments.component').then(m => m.DepartmentsComponent)
  },
  {
    path: 'doctors/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./features/providers/doctor-profile.component').then(m => m.DoctorProfileComponent)
  },
  {
    path: 'doctor-approvals',
    canActivate: [authGuard, roleGuard('ADMIN', 'STAFF')],
    loadComponent: () => import('./features/providers/doctor-approvals.component').then(m => m.DoctorApprovalsComponent)
  },
  {
    path: 'staff',
    canActivate: [authGuard, roleGuard('ADMIN', 'STAFF')],
    loadComponent: () => import('./features/staff/staff-directory.component').then(m => m.StaffDirectoryComponent)
  },
  {
    path: 'staff/new',
    canActivate: [authGuard, roleGuard('ADMIN')],
    loadComponent: () => import('./features/staff/hire-staff.component').then(m => m.HireStaffComponent)
  },
  {
    path: 'my-profile',
    canActivate: [authGuard, roleGuard('PATIENT')],
    loadComponent: () => import('./features/patients/my-profile.component').then(m => m.MyProfileComponent)
  },
  { path: '**', redirectTo: '' }
];
