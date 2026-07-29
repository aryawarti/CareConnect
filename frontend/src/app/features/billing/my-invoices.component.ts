import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { BillingService } from '../../core/billing/billing.service';
import { Invoice } from '../../core/billing/billing.models';

@Component({
  selector: 'cc-my-invoices',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, MatCardModule, MatButtonModule, MatChipsModule,
            MatIconModule, MatSnackBarModule],
  template: `
    <div class="cc-page" style="max-width:760px">
      <div class="cc-page-head"><div><h1>My invoices</h1><div class="cc-sub">Bills for your visits</div></div></div>
      @for (inv of invoices(); track inv.id) {
        <mat-card appearance="outlined">
          <mat-card-content style="display:flex;align-items:center;gap:16px;padding-top:16px">
            <div style="flex:1">
              <strong>{{ inv.invoiceNumber }}</strong> — {{ inv.doctorName }}<br>
              <span style="color:#666">Issued {{ inv.issuedAt | date:'mediumDate' }}</span>
              @if (inv.paidAt) {
                <span style="color:#666"> · Paid {{ inv.paidAt | date:'mediumDate' }}</span>
              }
            </div>
            <strong>{{ inv.amount | currency:'INR':'symbol':'1.2-2' }}</strong>
            <span class="cc-pill" [class]="inv.status">{{ inv.status }}</span>
            @if (inv.status === 'ISSUED') {
              <button mat-flat-button class="cc-btn-primary" [disabled]="paying() === inv.id"
                      (click)="pay(inv)">
                Pay now
              </button>
            }
          </mat-card-content>
        </mat-card>
      } @empty {
        <mat-card appearance="outlined">
          <mat-card-content>
            <p>No invoices yet. One is issued automatically after a completed visit.</p>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `
})
export class MyInvoicesComponent {
  private readonly service = inject(BillingService);
  private readonly snackBar = inject(MatSnackBar);

  readonly invoices = signal<Invoice[]>([]);
  readonly paying = signal<string | null>(null);

  constructor() {
    this.reload();
  }

  pay(invoice: Invoice): void {
    this.paying.set(invoice.id);
    this.service.pay(invoice.id, invoice.amount).subscribe({
      next: () => {
        this.paying.set(null);
        this.snackBar.open(`Paid ${invoice.invoiceNumber} — receipt emailed`, 'OK',
            { duration: 4000 });
        this.reload();
      },
      error: err => {
        this.paying.set(null);
        this.snackBar.open(err?.error?.detail ?? 'Payment failed', 'OK', { duration: 4000 });
        this.reload();
      }
    });
  }

  private reload(): void {
    this.service.myInvoices().subscribe(r => this.invoices.set(r.data));
  }
}
