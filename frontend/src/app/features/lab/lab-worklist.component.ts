import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { LabService } from '../../core/lab/lab.service';
import { CatalogueTest, LabOrder, OrderItem, ResultEntry } from '../../core/lab/lab.models';
import { StatComponent, EmptyStateComponent } from '../../shared/ui.components';

/**
 * The bench technician's screen. The worklist is every order not yet released,
 * grouped by where it is in the pipeline. Selecting an order opens the actions
 * valid for its current status — the state machine is enforced server-side, so
 * the UI only ever offers the legal next step.
 */
@Component({
  selector: 'cc-lab-worklist',
  standalone: true,
  imports: [DatePipe, FormsModule, MatButtonModule, MatIconModule,
            MatFormFieldModule, MatInputModule, MatSelectModule, MatSnackBarModule,
            StatComponent, EmptyStateComponent],
  template: `
    <div class="cc-page">
      <div class="cc-page-head">
        <div><h1>Lab worklist</h1><div class="cc-sub">Sample collection, processing and result entry</div></div>
        <span class="cc-spacer"></span>
        <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Refresh</button>
      </div>

      <div class="cc-grid cc-grid-4" style="margin-bottom:20px">
        <cc-stat icon="pending" [value]="byStatus('ORDERED').length" label="Awaiting collection" tone="warn" />
        <cc-stat icon="science" [value]="byStatus('COLLECTED').length + byStatus('IN_PROCESS').length"
                 label="In the lab" tone="info" />
        <cc-stat icon="fact_check" [value]="byStatus('REPORTED').length" label="Awaiting verification" tone="accent" />
        <cc-stat icon="priority_high" [value]="statCount()" label="Urgent / STAT" tone="danger" />
      </div>

      <div class="cc-grid cc-grid-2" style="align-items:start">
        <div class="cc-card">
          <h3>Orders</h3>
          @if (orders().length) {
            <div class="cc-stack" style="margin-top:10px">
              @for (o of orders(); track o.id) {
                <div class="cc-row" style="padding:10px 0;border-bottom:1px solid var(--cc-line);cursor:pointer"
                     [style.background]="selected()?.id === o.id ? 'var(--cc-canvas)' : ''"
                     (click)="open(o)">
                  <div style="flex:1">
                    <div style="font-weight:600">
                      {{ o.orderNumber }}
                      @if (o.priority !== 'ROUTINE') {
                        <span class="cc-pill CRITICAL">{{ o.priority }}</span>
                      }
                    </div>
                    <div class="cc-faint">{{ o.patientName }} · {{ testNames(o) }}</div>
                  </div>
                  <span class="cc-pill" [class]="o.status">{{ o.status }}</span>
                </div>
              }
            </div>
          } @else {
            <cc-empty icon="task_alt" title="Worklist clear" text="No pending lab orders right now." />
          }
        </div>

        <div class="cc-card">
          @if (selected(); as o) {
            <div class="cc-row">
              <h3 style="flex:1">{{ o.orderNumber }}</h3>
              <span class="cc-pill" [class]="o.status">{{ o.status }}</span>
            </div>
            <div class="cc-faint" style="margin-bottom:12px">
              {{ o.patientName }} · ordered by {{ o.doctorName }}
              · {{ o.orderedAt | date:'MMM d, h:mm a' }}
              @if (o.clinicalIndication) { <div>Indication: {{ o.clinicalIndication }}</div> }
            </div>

            @if (o.status === 'ORDERED') {
              <div class="cc-row" style="gap:12px;align-items:baseline">
                <mat-form-field appearance="outline" style="flex:1">
                  <mat-label>Specimen type</mat-label>
                  <input matInput [(ngModel)]="specimen" placeholder="e.g. Whole blood (EDTA)">
                </mat-form-field>
                <button mat-flat-button class="cc-btn-primary" [disabled]="busy()" (click)="collect(o)">
                  <mat-icon>colorize</mat-icon> Collect sample
                </button>
                <button mat-stroked-button [disabled]="busy()" (click)="reject(o)">Reject</button>
              </div>
            }

            @if (o.status === 'COLLECTED') {
              @for (s of o.samples; track s.accessionNo) {
                <div class="cc-faint">Accession {{ s.accessionNo }} · {{ s.specimenType }}</div>
              }
              <button mat-flat-button class="cc-btn-primary" [disabled]="busy()" (click)="process(o)"
                      style="margin-top:8px">
                <mat-icon>biotech</mat-icon> Begin processing
              </button>
            }

            @if (o.status === 'IN_PROCESS' || o.status === 'REPORTED') {
              @for (item of o.items; track item.id) {
                <div style="margin:12px 0;padding:10px;border:1px solid var(--cc-line);border-radius:8px">
                  <div style="font-weight:600;margin-bottom:6px">{{ item.testName }} ({{ item.testCode }})</div>
                  @for (a of analytesFor(item); track a.analyteId) {
                    <div class="cc-row" style="gap:10px;align-items:baseline">
                      <span style="flex:1">{{ a.name }} <span class="cc-faint">{{ a.unit }}</span></span>
                      <mat-form-field appearance="outline" style="width:140px" subscriptSizing="dynamic">
                        <input matInput [(ngModel)]="a.value" [placeholder]="a.ref">
                      </mat-form-field>
                    </div>
                  }
                  <button mat-stroked-button [disabled]="busy()" (click)="submitItem(o, item)"
                          style="margin-top:6px">
                    <mat-icon>save</mat-icon> Save {{ item.testName }} results
                  </button>
                  @if (resultsOf(item).length) {
                    <div style="margin-top:8px">
                      @for (r of resultsOf(item); track r.analyteName) {
                        <div class="cc-row" style="font-size:13px">
                          <span style="flex:1">{{ r.analyteName }}</span>
                          <span [style.color]="flagColor(r.flag)" style="font-weight:600">
                            {{ r.value }} {{ r.unit }}
                            @if (r.flag && r.flag !== 'NORMAL') { ({{ r.flag }}) }
                          </span>
                        </div>
                      }
                    </div>
                  }
                </div>
              }
              @if (o.status === 'REPORTED') {
                <div class="cc-card" style="background:var(--cc-canvas);padding:12px;margin-top:8px">
                  <div style="font-weight:600">Verify &amp; release</div>
                  <div class="cc-faint" style="margin-bottom:8px">
                    Verification releases the report to the patient and the ordering doctor.
                  </div>
                  <button mat-flat-button class="cc-btn-primary" [disabled]="busy()" (click)="verify(o)">
                    <mat-icon>verified</mat-icon> Verify and release
                  </button>
                </div>
              }
            }
          } @else {
            <cc-empty icon="arrow_back" title="Select an order"
                      text="Pick an order from the worklist to act on it." />
          }
        </div>
      </div>
    </div>
  `
})
export class LabWorklistComponent {
  private readonly lab = inject(LabService);
  private readonly snackBar = inject(MatSnackBar);

  readonly orders = signal<LabOrder[]>([]);
  readonly selected = signal<LabOrder | null>(null);
  readonly catalogue = signal<CatalogueTest[]>([]);
  readonly busy = signal(false);
  specimen = '';

  // Working copy of analyte entry rows, keyed by order-item id.
  private entry = new Map<string, { analyteId: string; name: string; unit: string; ref: string; value: string }[]>();

  readonly statCount = computed(() =>
    this.orders().filter(o => o.priority !== 'ROUTINE').length);

  constructor() {
    this.lab.catalogue().subscribe(c => this.catalogue.set(c));
    this.load();
  }

  load(): void {
    this.lab.worklist().subscribe({
      next: list => {
        this.orders.set(list);
        const cur = this.selected();
        if (cur) {
          this.selected.set(list.find(o => o.id === cur.id) ?? null);
        }
      },
      error: () => this.orders.set([])
    });
  }

  byStatus(s: string): LabOrder[] {
    return this.orders().filter(o => o.status === s);
  }

  testNames(o: LabOrder): string {
    return o.items.map(i => i.testName).join(', ');
  }

  open(o: LabOrder): void {
    this.entry.clear();
    this.selected.set(o);
  }

  /** Build the analyte entry rows for a test from the catalogue definition. */
  analytesFor(item: OrderItem) {
    if (this.entry.has(item.id)) {
      return this.entry.get(item.id)!;
    }
    const test = this.catalogue().find(t => t.code === item.testCode);
    const rows = (test?.analytes ?? []).map(a => ({
      analyteId: a.id,
      name: a.name,
      unit: a.unit,
      ref: a.refLow != null && a.refHigh != null ? `${a.refLow}–${a.refHigh}` : '',
      value: ''
    }));
    this.entry.set(item.id, rows);
    return rows;
  }

  resultsOf(item: OrderItem) {
    return item.results ?? [];
  }

  flagColor(flag: string | null): string {
    switch (flag) {
      case 'CRITICAL': return 'var(--cc-danger)';
      case 'HIGH': case 'LOW': return 'var(--cc-warn)';
      default: return 'var(--cc-ink)';
    }
  }

  collect(o: LabOrder): void {
    if (!this.specimen.trim()) {
      this.snackBar.open('Enter a specimen type', 'OK', { duration: 3000 });
      return;
    }
    this.run(this.lab.collect(o.id, this.specimen), 'Sample collected', () => (this.specimen = ''));
  }

  process(o: LabOrder): void {
    this.run(this.lab.beginProcessing(o.id), 'Processing started');
  }

  reject(o: LabOrder): void {
    const reason = window.prompt('Reason for rejecting this order?') ?? '';
    if (!reason.trim()) { return; }
    this.run(this.lab.reject(o.id, reason), 'Order rejected');
  }

  submitItem(o: LabOrder, item: OrderItem): void {
    const rows = this.analytesFor(item).filter(r => r.value.trim());
    if (!rows.length) {
      this.snackBar.open('Enter at least one value', 'OK', { duration: 3000 });
      return;
    }
    const results: ResultEntry[] = rows.map(r => ({ analyteId: r.analyteId, value: r.value.trim() }));
    this.run(this.lab.enterResults(o.id, { orderItemId: item.id, results }),
      `Results saved for ${item.testName}`);
  }

  verify(o: LabOrder): void {
    this.run(this.lab.verify(o.id), 'Report verified and released');
  }

  private run(obs: ReturnType<LabService['collect']>, ok: string, after?: () => void): void {
    this.busy.set(true);
    obs.subscribe({
      next: updated => {
        this.snackBar.open(ok, 'OK', { duration: 3000 });
        this.entry.clear();
        this.selected.set(updated);
        this.busy.set(false);
        after?.();
        this.load();
      },
      error: err => {
        this.snackBar.open(err?.error?.detail ?? 'Action failed', 'OK', { duration: 4000 });
        this.busy.set(false);
      }
    });
  }
}
