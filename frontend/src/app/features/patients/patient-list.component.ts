import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { PatientsService } from '../../core/patients/patients.service';
import { PageMeta, Patient } from '../../core/patients/patient.models';
import { asyncResource } from '../../core/http/async-resource';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/ui.components';

/**
 * Patient registry. The search box is why this screen needs `refreshing` rather
 * than `loading`: blanking the table to a skeleton on every keystroke makes the
 * page flicker and loses the user's place. Existing rows stay, dimmed, until the
 * new page arrives — and because AsyncResource cancels the previous request,
 * results can't arrive out of order and show the wrong page for the query.
 */
@Component({
  selector: 'cc-patient-list',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, MatTableModule, MatPaginatorModule,
            MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
            MatTooltipModule, SkeletonComponent, ErrorPanelComponent, EmptyStateComponent],
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

      @if (patients.loading()) {
        <cc-skeleton [count]="6" label="Loading patients…" />
      } @else if (patients.failed()) {
        <cc-error [message]="patients.error()!" (retry)="patients.reload()" />
      } @else if (rows().length) {
        <div class="cc-table-wrap" [class.cc-stale]="patients.refreshing()"
             [attr.aria-busy]="patients.refreshing()">
          <table mat-table [dataSource]="rows()" style="width:100%">
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
                <a mat-icon-button [routerLink]="['/patients', p.id]"
                   [attr.aria-label]="'Edit ' + p.firstName + ' ' + p.lastName">
                  <mat-icon>edit</mat-icon>
                </a>
                <a mat-icon-button [routerLink]="['/patients', p.id, 'access']"
                   matTooltip="Who has seen this chart"
                   [attr.aria-label]="'Chart access trail for ' + p.firstName + ' ' + p.lastName">
                  <mat-icon>shield_person</mat-icon>
                </a>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        </div>

        @if (patients.value()?.meta; as m) {
          <mat-paginator [length]="m.totalElements" [pageIndex]="m.page" [pageSize]="m.size"
                         [pageSizeOptions]="[10, 20, 50]" (page)="onPage($event)" />
        }
      } @else if (query.value) {
        <cc-empty icon="search_off" title="No patients match “{{ query.value }}”"
                  text="Try part of a surname, a phone number, or a full MRN." />
      } @else {
        <cc-empty icon="group" title="No patients registered yet"
                  text="Register the first patient to start booking appointments.">
          <a mat-flat-button class="cc-btn-primary" routerLink="/patients/new">
            <mat-icon>person_add</mat-icon> Register a patient
          </a>
        </cc-empty>
      }
    </div>
  `
})
export class PatientListComponent {
  private readonly service = inject(PatientsService);

  readonly columns = ['mrn', 'name', 'dob', 'phone', 'status', 'actions'];
  readonly query = new FormControl('', { nonNullable: true });

  private page = 0;
  private size = 20;

  readonly patients = asyncResource<{ data: Patient[]; meta: PageMeta }>(() =>
    this.service.search(this.query.value, this.page, this.size));

  constructor() {
    this.query.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.page = 0;
        this.patients.reload();
      });
  }

  /** Rows currently on screen, including stale ones during a refresh. */
  rows(): Patient[] {
    return this.patients.value()?.data ?? [];
  }

  onPage(e: PageEvent): void {
    this.page = e.pageIndex;
    this.size = e.pageSize;
    this.patients.reload();
  }
}
