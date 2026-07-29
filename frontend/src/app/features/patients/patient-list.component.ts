import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { PatientsService } from '../../core/patients/patients.service';
import { PageMeta, Patient } from '../../core/patients/patient.models';

@Component({
  selector: 'cc-patient-list',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, MatTableModule, MatPaginatorModule,
            MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatChipsModule],
  template: `
    <div class="cc-page">
      <div class="cc-page-head">
        <div><h1>Patients</h1><div class="cc-sub">Registry and demographics</div></div>
        <span class="cc-spacer"></span>
        <a mat-flat-button class="cc-btn-primary" routerLink="/patients/new">
          <mat-icon>person_add</mat-icon> New patient
        </a>
      </div>

      <mat-form-field appearance="outline" class="cc-full-width">
        <mat-label>Search by name, phone, or MRN</mat-label>
        <input matInput [formControl]="query" placeholder="e.g. Sharma, 98765…, P-100001">
        <mat-icon matSuffix>search</mat-icon>
      </mat-form-field>

      <div class="cc-table-wrap">
      <table mat-table [dataSource]="patients()" style="width:100%">
        <ng-container matColumnDef="mrn">
          <th mat-header-cell *matHeaderCellDef>MRN</th>
          <td mat-cell *matCellDef="let p">{{ p.patientNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let p">{{ p.lastName }}, {{ p.firstName }}</td>
        </ng-container>
        <ng-container matColumnDef="dob">
          <th mat-header-cell *matHeaderCellDef>Date of birth</th>
          <td mat-cell *matCellDef="let p">{{ p.dateOfBirth | date:'mediumDate' }}</td>
        </ng-container>
        <ng-container matColumnDef="phone">
          <th mat-header-cell *matHeaderCellDef>Phone</th>
          <td mat-cell *matCellDef="let p">{{ p.phone ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let p">
            <span class="cc-pill" [class]="p.status">{{ p.status }}</span>
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let p">
            <a mat-icon-button [routerLink]="['/patients', p.id]" aria-label="Edit">
              <mat-icon>edit</mat-icon>
            </a>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      </div>

      @if (meta(); as m) {
        <mat-paginator [length]="m.totalElements" [pageIndex]="m.page" [pageSize]="m.size"
                       [pageSizeOptions]="[10, 20, 50]" (page)="onPage($event)" />
      }
    </div>
  `
})
export class PatientListComponent {
  private readonly service = inject(PatientsService);

  readonly columns = ['mrn', 'name', 'dob', 'phone', 'status', 'actions'];
  readonly patients = signal<Patient[]>([]);
  readonly meta = signal<PageMeta | null>(null);
  readonly query = new FormControl('', { nonNullable: true });

  private page = 0;
  private size = 20;

  constructor() {
    this.load();
    this.query.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => { this.page = 0; this.load(); });
  }

  onPage(e: PageEvent): void {
    this.page = e.pageIndex;
    this.size = e.pageSize;
    this.load();
  }

  private load(): void {
    this.service.search(this.query.value, this.page, this.size).subscribe(r => {
      this.patients.set(r.data);
      this.meta.set(r.meta);
    });
  }
}
