import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { LabService } from '../../core/lab/lab.service';
import { LabOrder } from '../../core/lab/lab.models';
import { EmptyStateComponent } from '../../shared/ui.components';

/**
 * A patient's released lab reports. Only VERIFIED orders reach here (the server
 * filters), so a patient never sees a provisional or unverified value. Abnormal
 * results are colour-coded but deliberately understated — the report tells them
 * to discuss anything flagged with their doctor rather than self-interpret.
 */
@Component({
  selector: 'cc-my-lab-results',
  standalone: true,
  imports: [DatePipe, MatCardModule, MatIconModule, MatExpansionModule, EmptyStateComponent],
  template: `
    <div class="cc-page" style="max-width:820px">
      <div class="cc-page-head">
        <div><h1>My lab results</h1><div class="cc-sub">Verified laboratory reports</div></div>
      </div>

      @if (reports().length) {
        <mat-accordion>
          @for (o of reports(); track o.id) {
            <mat-expansion-panel [expanded]="$first">
              <mat-expansion-panel-header>
                <mat-panel-title>
                  <mat-icon style="margin-right:8px;color:var(--cc-primary)">description</mat-icon>
                  {{ testNames(o) }}
                </mat-panel-title>
                <mat-panel-description>
                  {{ o.orderedAt | date:'MMM d, y' }} · {{ o.orderNumber }}
                </mat-panel-description>
              </mat-expansion-panel-header>

              <div class="cc-faint" style="margin-bottom:8px">
                Ordered by {{ o.doctorName }}
              </div>

              @for (item of o.items; track item.id) {
                <div style="margin-bottom:14px">
                  <div style="font-weight:600;margin-bottom:4px">{{ item.testName }}</div>
                  <table style="width:100%;border-collapse:collapse;font-size:14px">
                    <thead>
                      <tr style="text-align:left;color:var(--cc-ink-soft)">
                        <th style="padding:4px 0">Analyte</th>
                        <th>Result</th>
                        <th>Reference</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (r of item.results; track r.analyteName) {
                        <tr style="border-top:1px solid var(--cc-line)">
                          <td style="padding:6px 0">{{ r.analyteName }}</td>
                          <td [style.color]="flagColor(r.flag)" style="font-weight:600">
                            {{ r.value }} {{ r.unit }}
                            @if (r.flag && r.flag !== 'NORMAL') {
                              <span style="font-size:12px"> ({{ r.flag }})</span>
                            }
                          </td>
                          <td class="cc-faint">
                            @if (r.refLow != null && r.refHigh != null) {
                              {{ r.refLow }}–{{ r.refHigh }} {{ r.unit }}
                            } @else { — }
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }

              @if (hasAbnormal(o)) {
                <div class="cc-row" style="gap:8px;background:var(--cc-canvas);padding:10px;border-radius:8px">
                  <mat-icon style="color:var(--cc-warn)">info</mat-icon>
                  <span style="font-size:13px">
                    Some values are outside the reference range. Please discuss these
                    results with your doctor — they are best interpreted in context.
                  </span>
                </div>
              }
            </mat-expansion-panel>
          }
        </mat-accordion>
      } @else {
        <cc-empty icon="biotech" title="No results yet"
                  text="When your doctor orders lab tests and the report is verified, it will appear here." />
      }
    </div>
  `
})
export class MyLabResultsComponent {
  private readonly lab = inject(LabService);
  readonly reports = signal<LabOrder[]>([]);

  constructor() {
    this.lab.myReports().subscribe({
      next: list => this.reports.set(list),
      error: () => this.reports.set([])
    });
  }

  testNames(o: LabOrder): string {
    return o.items.map(i => i.testName).join(', ');
  }

  hasAbnormal(o: LabOrder): boolean {
    return o.items.some(i => i.results.some(r => r.flag && r.flag !== 'NORMAL'));
  }

  flagColor(flag: string | null): string {
    switch (flag) {
      case 'CRITICAL': return 'var(--cc-danger)';
      case 'HIGH': case 'LOW': return 'var(--cc-warn)';
      default: return 'var(--cc-ink)';
    }
  }
}
