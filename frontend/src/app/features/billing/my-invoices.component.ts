import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { map } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { BillingService } from '../../core/billing/billing.service';
import { Invoice } from '../../core/billing/billing.models';
import { asyncResource } from '../../core/http/async-resource';
import { humanizeError } from '../../core/http/http-status';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/ui.components';

@Component({
  selector: 'cc-my-invoices',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, MatCardModule, MatButtonModule, MatIconModule,
            MatSnackBarModule, SkeletonComponent, ErrorPanelComponent, EmptyStateComponent],
  template: `
    <div class="cc-page cc-narrow">
      <div class="cc-page-head">
        <div><h1>My invoices</h1><div class="cc-sub">Bills for your visits</div></div>
      </div>

      @if (invoices.loading()) {
        <cc-skeleton [count]="3" variant="card" label="Loading your invoices…" />
      } @else if (invoices.failed()) {
        <cc-error [message]="invoices.error()!" (retry)="invoices.reload()" />
      } @else if (invoices.value()?.length) {
        <div [class.cc-stale]="invoices.refreshing()">
          @for (inv of invoices.value(); track inv.id) {
            <mat-card appearance="outlined">
              <mat-card-content class="cc-row" style="padding-top:16px">
                <div style="flex:1">
                  <strong>{{ inv.invoiceNumber }}</strong> — {{ inv.doctorName }}<br>
                  <span class="cc-muted">Issued {{ inv.issuedAt | date:'mediumDate' }}</span>
                  @if (inv.paidAt) {
                    <span class="cc-muted"> · Paid {{ inv.paidAt | date:'mediumDate' }}</span>
                  }
                </div>
                <strong class="cc-money">
                  {{ inv.amount | currency:'INR':'symbol':'1.2-2' }}
                </strong>
                <span class="cc-pill" [class]="inv.status">{{ inv.status }}</span>
                @if (inv.status === 'ISSUED') {
                  <button mat-flat-button class="cc-btn-primary"
                          [disabled]="paying() !== null"
                          [attr.aria-busy]="paying() === inv.id"
                          (click)="pay(inv)">
                    {{ paying() === inv.id ? 'Paying…' : 'Pay now' }}
                  </button>
                }
              </mat-card-content>
            </mat-card>
          }
        </div>
      } @else {
        <cc-empty icon="receipt_long" title="No invoices yet"
                  text="An invoice is issued automatically once a visit is completed." />
      }
    </div>
  `
})
export class MyInvoicesComponent {
  private readonly service = inject(BillingService);
  private readonly snackBar = inject(MatSnackBar);

  readonly invoices = asyncResource(() => this.service.myInvoices().pipe(map(r => r.data)));

  /**
   * Which invoice is being paid. Every Pay button is disabled while any payment
   * is in flight — paying twice is the one mistake on this screen that costs the
   * patient money, and the server's idempotency reference is the backstop, not
   * the first line of defence.
   */
  readonly paying = signal<string | null>(null);

  pay(invoice: Invoice): void {
    if (this.paying()) {
      return;
    }
    this.paying.set(invoice.id);
    this.service.pay(invoice.id, invoice.amount).subscribe({
      next: () => {
        this.paying.set(null);
        this.snackBar.open(`Paid ${invoice.invoiceNumber} — receipt emailed`, 'OK',
            { duration: 4000 });
        this.invoices.reload();
      },
      error: err => {
        this.paying.set(null);
        this.snackBar.open(humanizeError(err), 'OK', { duration: 5000 });
        // Refresh regardless: a duplicate submit is rejected by the server's
        // unique payment reference, and the invoice may already be paid.
        this.invoices.reload();
      }
    });
  }
}
