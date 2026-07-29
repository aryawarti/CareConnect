import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { BillingService } from '../../core/billing/billing.service';
import { Invoice } from '../../core/billing/billing.models';

@Component({
  selector: 'cc-billing-admin',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, MatTableModule, MatFormFieldModule,
            MatSelectModule, MatButtonModule, MatChipsModule, MatSnackBarModule],
  template: `
    <div class="cc-page">
      <div class="cc-page-head"><div><h1>Billing</h1><div class="cc-sub">Invoices and collections</div></div></div>
      <mat-form-field appearance="outline" style="width:220px">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtl">
          <mat-option value="ISSUED">Outstanding</mat-option>
          <mat-option value="PAID">Paid</mat-option>
          <mat-option value="VOID">Void</mat-option>
        </mat-select>
      </mat-form-field>

      <div class="cc-table-wrap">
      <table mat-table [dataSource]="invoices()" style="width:100%">
        <ng-container matColumnDef="number">
          <th mat-header-cell *matHeaderCellDef>Invoice</th>
          <td mat-cell *matCellDef="let i">{{ i.invoiceNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="patient">
          <th mat-header-cell *matHeaderCellDef>Patient</th>
          <td mat-cell *matCellDef="let i">{{ i.patientName }}</td>
        </ng-container>
        <ng-container matColumnDef="doctor">
          <th mat-header-cell *matHeaderCellDef>Doctor</th>
          <td mat-cell *matCellDef="let i">{{ i.doctorName }}</td>
        </ng-container>
        <ng-container matColumnDef="issued">
          <th mat-header-cell *matHeaderCellDef>Issued</th>
          <td mat-cell *matCellDef="let i">{{ i.issuedAt | date:'mediumDate' }}</td>
        </ng-container>
        <ng-container matColumnDef="amount">
          <th mat-header-cell *matHeaderCellDef>Amount</th>
          <td mat-cell *matCellDef="let i">{{ i.amount | currency:'INR':'symbol':'1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let i"><span class="cc-pill" [class]="i.status">{{ i.status }}</span></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let i">
            @if (i.status === 'ISSUED') {
              <button mat-button (click)="collect(i)">Mark paid</button>
              <button mat-button color="warn" (click)="voidInvoice(i)">Void</button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      </div>
      @if (!invoices().length) {
        <p style="color:#666;margin-top:16px">No invoices with this status.</p>
      }
    </div>
  `
})
export class BillingAdminComponent {
  private readonly service = inject(BillingService);
  private readonly snackBar = inject(MatSnackBar);

  readonly columns = ['number', 'patient', 'doctor', 'issued', 'amount', 'status', 'actions'];
  readonly invoices = signal<Invoice[]>([]);
  readonly statusCtl = new FormControl('ISSUED', { nonNullable: true });

  constructor() {
    this.reload();
    this.statusCtl.valueChanges.subscribe(() => this.reload());
  }

  collect(invoice: Invoice): void {
    this.service.pay(invoice.id, invoice.amount).subscribe({
      next: () => { this.snackBar.open('Payment recorded', 'OK', { duration: 3000 }); this.reload(); },
      error: err => this.snackBar.open(err?.error?.detail ?? 'Failed', 'OK', { duration: 4000 })
    });
  }

  voidInvoice(invoice: Invoice): void {
    this.service.voidInvoice(invoice.id, 'Voided by staff').subscribe({
      next: () => { this.snackBar.open('Invoice voided', 'OK', { duration: 3000 }); this.reload(); },
      error: err => this.snackBar.open(err?.error?.detail ?? 'Failed', 'OK', { duration: 4000 })
    });
  }

  private reload(): void {
    this.service.byStatus(this.statusCtl.value).subscribe(r => this.invoices.set(r.data));
  }
}
