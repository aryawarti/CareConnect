import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { BillingService } from '../../core/billing/billing.service';
import { Invoice } from '../../core/billing/billing.models';
import { asyncResource } from '../../core/http/async-resource';
import { humanizeError } from '../../core/http/http-status';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/ui.components';

/** Staff collections console: outstanding invoices, mark paid, void. */
@Component({
  selector: 'cc-billing-admin',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, MatTableModule, MatFormFieldModule,
            MatSelectModule, MatButtonModule, MatSnackBarModule,
            SkeletonComponent, ErrorPanelComponent, EmptyStateComponent],
  template: `
    <div class="cc-page">
      <div class="cc-page-head">
        <div><h1>Billing</h1><div class="cc-sub">Invoices and collections</div></div>
      </div>

      <mat-form-field appearance="outline" style="width:220px">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtl">
          <mat-option value="ISSUED">Outstanding</mat-option>
          <mat-option value="PAID">Paid</mat-option>
          <mat-option value="VOID">Void</mat-option>
        </mat-select>
      </mat-form-field>

      @if (invoices.loading()) {
        <cc-skeleton [count]="6" label="Loading invoices…" />
      } @else if (invoices.failed()) {
        <cc-error [message]="invoices.error()!" (retry)="invoices.reload()" />
      } @else if (rows().length) {
        <div class="cc-table-wrap" [class.cc-stale]="invoices.refreshing()">
          <table mat-table [dataSource]="rows()" style="width:100%">
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
              <td mat-cell *matCellDef="let i" class="cc-money">
                {{ i.amount | currency:'INR':'symbol':'1.2-2' }}
              </td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let i">
                <span class="cc-pill" [class]="i.status">{{ i.status }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let i">
                @if (i.status === 'ISSUED') {
                  <button mat-button [disabled]="busy() !== null" (click)="collect(i)">
                    {{ busy() === i.id ? 'Recording…' : 'Mark paid' }}
                  </button>
                  <button mat-button [disabled]="busy() !== null" (click)="voidInvoice(i)">
                    Void
                  </button>
                }
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        </div>
      } @else {
        <cc-empty icon="receipt_long" [title]="emptyTitle()"
                  text="Invoices are issued automatically when a visit is completed." />
      }
    </div>
  `
})
export class BillingAdminComponent {
  private readonly service = inject(BillingService);
  private readonly snackBar = inject(MatSnackBar);

  readonly columns = ['number', 'patient', 'doctor', 'issued', 'amount', 'status', 'actions'];
  readonly statusCtl = new FormControl('ISSUED', { nonNullable: true });

  readonly invoices = asyncResource(() =>
    this.service.byStatus(this.statusCtl.value).pipe(map(r => r.data)));

  /** Invoice being acted on — money actions must not be double-submitted. */
  readonly busy = signal<string | null>(null);

  constructor() {
    this.statusCtl.valueChanges.pipe(takeUntilDestroyed())
      .subscribe(() => this.invoices.reload());
  }

  rows(): Invoice[] {
    return this.invoices.value() ?? [];
  }

  emptyTitle(): string {
    switch (this.statusCtl.value) {
      case 'PAID': return 'Nothing paid yet';
      case 'VOID': return 'No voided invoices';
      default: return 'Nothing outstanding';
    }
  }

  collect(invoice: Invoice): void {
    this.act(invoice, this.service.pay(invoice.id, invoice.amount), 'Payment recorded');
  }

  voidInvoice(invoice: Invoice): void {
    this.act(invoice, this.service.voidInvoice(invoice.id, 'Voided by staff'), 'Invoice voided');
  }

  private act(invoice: Invoice, action: ReturnType<BillingService['pay']>, done: string): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(invoice.id);
    action.subscribe({
      next: () => {
        this.busy.set(null);
        this.snackBar.open(done, 'OK', { duration: 3000 });
        this.invoices.reload();
      },
      error: err => {
        this.busy.set(null);
        this.snackBar.open(humanizeError(err), 'OK', { duration: 5000 });
        this.invoices.reload();
      }
    });
  }
}
