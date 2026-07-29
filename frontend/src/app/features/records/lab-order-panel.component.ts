import { Component, inject, input, signal, computed, OnInit } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { FormsModule } from '@angular/forms';
import { LabService } from '../../core/lab/lab.service';
import { CatalogueTest, LabOrder } from '../../core/lab/lab.models';

/**
 * Embedded in the encounter editor. Lets a doctor order lab tests for the
 * patient they are charting and see the status of tests already ordered for
 * this encounter — the first half of the clinical flow
 * (Consultation → Lab Tests → Results).
 */
@Component({
  selector: 'cc-lab-order-panel',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, FormsModule, MatButtonModule, MatIconModule,
            MatFormFieldModule, MatSelectModule, MatInputModule, MatCheckboxModule,
            MatSnackBarModule],
  template: `
    <h4 style="display:flex;align-items:center;gap:8px">
      <mat-icon style="color:var(--cc-primary)">biotech</mat-icon> Laboratory
    </h4>

    @if (orders().length) {
      <div class="cc-stack" style="margin-bottom:12px">
        @for (o of orders(); track o.id) {
          <div class="cc-row" style="padding:8px 0;border-bottom:1px solid var(--cc-line)">
            <div style="flex:1">
              <div style="font-weight:600">
                {{ o.orderNumber }}
                <span class="cc-pill" [class]="o.status">{{ o.status }}</span>
              </div>
              <div class="cc-faint">
                {{ testNames(o) }} · ordered {{ o.orderedAt | date:'MMM d, h:mm a' }}
              </div>
            </div>
            @if (o.status === 'VERIFIED') {
              <span style="color:var(--cc-ok);font-weight:600;font-size:13px">Results ready</span>
            }
          </div>
          @if (o.status === 'VERIFIED') {
            <div style="margin:4px 0 10px 8px">
              @for (item of o.items; track item.id) {
                @for (r of item.results; track r.analyteName) {
                  <div class="cc-row" style="font-size:13px;padding:2px 0">
                    <span style="flex:1">{{ item.testName }} — {{ r.analyteName }}</span>
                    <span [style.color]="flagColor(r.flag)" style="font-weight:600">
                      {{ r.value }} {{ r.unit }}
                      @if (r.flag && r.flag !== 'NORMAL') { <span>({{ r.flag }})</span> }
                    </span>
                  </div>
                }
              }
            </div>
          }
        }
      </div>
    }

    @if (canOrder()) {
      <div class="cc-card" style="background:var(--cc-canvas);padding:12px">
        <div style="font-weight:600;margin-bottom:8px">Order new tests</div>
        <mat-form-field appearance="outline" class="cc-full-width">
          <mat-label>Tests</mat-label>
          <mat-select [(ngModel)]="selectedIds" multiple>
            @for (t of catalogue(); track t.id) {
              <mat-option [value]="t.id">
                {{ t.name }} ({{ t.code }}) — {{ t.price | currency:'INR':'symbol':'1.0-0' }}
              </mat-option>
            }
          </mat-select>
        </mat-form-field>
        <div class="cc-row" style="gap:12px;align-items:baseline">
          <mat-form-field appearance="outline" style="width:150px">
            <mat-label>Priority</mat-label>
            <mat-select [(ngModel)]="priority">
              <mat-option value="ROUTINE">Routine</mat-option>
              <mat-option value="URGENT">Urgent</mat-option>
              <mat-option value="STAT">STAT</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" style="flex:1">
            <mat-label>Clinical indication</mat-label>
            <input matInput [(ngModel)]="indication" placeholder="e.g. suspected anaemia">
          </mat-form-field>
          <button mat-flat-button class="cc-btn-primary" [disabled]="!selectedIds.length || placing()"
                  (click)="place()">
            <mat-icon>send</mat-icon> Order
          </button>
        </div>
        @if (selectedIds.length) {
          <div class="cc-faint" style="margin-top:4px">
            Estimated total: {{ estimate() | currency:'INR':'symbol':'1.0-0' }}
          </div>
        }
      </div>
    }
  `
})
export class LabOrderPanelComponent implements OnInit {
  private readonly lab = inject(LabService);
  private readonly snackBar = inject(MatSnackBar);

  readonly encounterId = input.required<string>();
  readonly patientId = input.required<string>();
  readonly canOrder = input<boolean>(true);

  readonly catalogue = signal<CatalogueTest[]>([]);
  readonly orders = signal<LabOrder[]>([]);
  readonly placing = signal(false);

  selectedIds: string[] = [];
  priority: 'ROUTINE' | 'URGENT' | 'STAT' = 'ROUTINE';
  indication = '';

  readonly estimate = computed(() =>
    this.catalogue().filter(t => this.selectedIds.includes(t.id))
      .reduce((sum, t) => sum + Number(t.price), 0));

  ngOnInit(): void {
    this.lab.catalogue().subscribe(c => this.catalogue.set(c));
    this.refresh();
  }

  private refresh(): void {
    this.lab.forPatient(this.patientId()).subscribe({
      next: list => this.orders.set(list.filter(o => o.encounterId === this.encounterId())),
      error: () => this.orders.set([])
    });
  }

  testNames(o: LabOrder): string {
    return o.items.map(i => i.testName).join(', ');
  }

  flagColor(flag: string | null): string {
    switch (flag) {
      case 'CRITICAL': return 'var(--cc-danger)';
      case 'HIGH': case 'LOW': return 'var(--cc-warn)';
      default: return 'var(--cc-ink)';
    }
  }

  place(): void {
    this.placing.set(true);
    this.lab.order({
      encounterId: this.encounterId(),
      patientId: this.patientId(),
      clinicalIndication: this.indication || undefined,
      priority: this.priority,
      testIds: this.selectedIds
    }).subscribe({
      next: () => {
        this.snackBar.open('Lab order placed', 'OK', { duration: 3000 });
        this.selectedIds = [];
        this.indication = '';
        this.placing.set(false);
        this.refresh();
      },
      error: err => {
        this.snackBar.open(err?.error?.detail ?? 'Order failed', 'OK', { duration: 4000 });
        this.placing.set(false);
      }
    });
  }
}
