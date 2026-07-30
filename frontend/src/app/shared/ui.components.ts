import { Component, input, output } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/** Small presentational building blocks shared by the dashboards and screens. */

/**
 * Loading placeholder shaped like the content it stands in for.
 *
 * Deliberately not a centred spinner: a spinner discards the layout, so the page
 * jumps when real content arrives. Blocks of roughly the right size keep the
 * layout stable, which is the difference between "loading" and "broken" to
 * someone watching.
 *
 * aria-busy + a polite live region, so this is announced rather than being a
 * silent gap for anyone using a screen reader.
 */
@Component({
  selector: 'cc-skeleton',
  standalone: true,
  template: `
    <div class="cc-skeleton" role="status" aria-busy="true" [attr.aria-label]="label()">
      @for (row of rows(); track $index) {
        <div class="cc-skeleton-row" [class.card]="variant() === 'card'"></div>
      }
    </div>
  `
})
export class SkeletonComponent {
  readonly count = input<number>(3);
  /** 'list' = text lines, 'card' = taller blocks. */
  readonly variant = input<'list' | 'card'>('list');
  readonly label = input<string>('Loading…');

  rows(): number[] {
    return Array.from({ length: this.count() }, (_, i) => i);
  }
}

/**
 * A failed request, reported where the user was looking, with a way out.
 *
 * The message comes from humanizeError, so it is a sentence about what to do
 * rather than a status code. Retry matters more than the wording: most failures
 * here are a service still warming up, and the honest fix is to ask again.
 */
@Component({
  selector: 'cc-error',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  template: `
    <div class="cc-alert cc-alert-error" role="alert">
      <mat-icon>error_outline</mat-icon>
      <div style="flex:1">
        <div>{{ message() }}</div>
        @if (canRetry()) {
          <button mat-stroked-button style="margin-top:10px" (click)="retry.emit()">
            <mat-icon>refresh</mat-icon> Try again
          </button>
        }
      </div>
    </div>
  `
})
export class ErrorPanelComponent {
  readonly message = input.required<string>();
  /** Explicit, because an output gives no way to ask whether anyone is listening. */
  readonly canRetry = input<boolean>(true);
  readonly retry = output<void>();
}

@Component({
  selector: 'cc-stat',
  standalone: true,
  imports: [MatIconModule],
  template: `
    <div class="cc-stat" [class]="tone()">
      <div class="cc-stat-icon"><mat-icon>{{ icon() }}</mat-icon></div>
      <div>
        <div class="cc-stat-value">{{ value() }}</div>
        <div class="cc-stat-label">{{ label() }}</div>
        @if (hint()) { <div class="cc-stat-hint">{{ hint() }}</div> }
      </div>
    </div>
  `
})
export class StatComponent {
  readonly icon = input.required<string>();
  /** `string | null` because Angular's currency/date pipes are nullable. */
  readonly value = input.required<string | number | null>();
  readonly label = input.required<string>();
  readonly hint = input<string>('');
  /** '' | accent | ok | info */
  readonly tone = input<string>('');
}

@Component({
  selector: 'cc-empty',
  standalone: true,
  imports: [MatIconModule],
  template: `
    <div class="cc-empty">
      <mat-icon>{{ icon() }}</mat-icon>
      <div class="cc-empty-title">{{ title() }}</div>
      <div class="cc-empty-text">{{ text() }}</div>
      <ng-content />
    </div>
  `
})
export class EmptyStateComponent {
  readonly icon = input<string>('inbox');
  readonly title = input.required<string>();
  readonly text = input<string>('');
}

/**
 * Hand-rolled SVG bar chart — no charting dependency for six bars.
 * Values are normalised against the max so the tallest bar always fills.
 */
@Component({
  selector: 'cc-bar-chart',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <div class="cc-card">
      <h3>{{ title() }}</h3>
      @if (subtitle()) { <div class="cc-faint" style="margin-bottom:14px">{{ subtitle() }}</div> }
      <div style="display:flex;align-items:flex-end;gap:10px;height:150px;margin-top:12px">
        @for (bar of data(); track bar.label) {
          <div style="flex:1;display:flex;flex-direction:column;align-items:center;gap:6px;height:100%">
            <div style="flex:1;display:flex;align-items:flex-end;width:100%">
              <div [style.height.%]="height(bar.value)"
                   [style.background]="bar.value ? 'var(--cc-primary)' : 'var(--cc-line)'"
                   [title]="bar.label + ': ' + bar.value"
                   style="width:100%;border-radius:6px 6px 0 0;min-height:3px;transition:height .3s ease">
              </div>
            </div>
            <div class="cc-faint" style="font-size:11px">{{ bar.label }}</div>
            <div style="font-size:12px;font-weight:600">{{ bar.value | number }}</div>
          </div>
        }
      </div>
    </div>
  `
})
export class BarChartComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string>('');
  readonly data = input.required<{ label: string; value: number }[]>();

  height(value: number): number {
    const max = Math.max(...this.data().map(d => d.value), 1);
    return Math.round((value / max) * 100);
  }
}

/** Donut chart for status breakdowns (appointments by state, invoices paid vs due). */
@Component({
  selector: 'cc-donut',
  standalone: true,
  template: `
    <div class="cc-card">
      <h3>{{ title() }}</h3>
      <div style="display:flex;align-items:center;gap:22px;margin-top:14px">
        <svg viewBox="0 0 42 42" style="width:132px;height:132px;flex:none">
          <circle cx="21" cy="21" r="15.915" fill="none" stroke="var(--cc-line)" stroke-width="5" />
          @for (arc of arcs(); track arc.label) {
            <circle cx="21" cy="21" r="15.915" fill="none"
                    [attr.stroke]="arc.color" stroke-width="5"
                    [attr.stroke-dasharray]="arc.dash"
                    [attr.stroke-dashoffset]="arc.offset"
                    transform="rotate(-90 21 21)" />
          }
          <text x="21" y="20.5" text-anchor="middle" style="font-size:7px;font-weight:700;fill:var(--cc-ink)">
            {{ total() }}
          </text>
          <text x="21" y="25.5" text-anchor="middle" style="font-size:3px;fill:var(--cc-ink-faint)">
            {{ centerLabel() }}
          </text>
        </svg>
        <div class="cc-stack" style="gap:8px">
          @for (slice of data(); track slice.label) {
            <div class="cc-row" style="gap:8px">
              <span [style.background]="slice.color"
                    style="width:10px;height:10px;border-radius:3px;display:inline-block"></span>
              <span style="font-size:13px">{{ slice.label }}</span>
              <strong style="font-size:13px">{{ slice.value }}</strong>
            </div>
          }
        </div>
      </div>
    </div>
  `
})
export class DonutComponent {
  readonly title = input.required<string>();
  readonly centerLabel = input<string>('total');
  readonly data = input.required<{ label: string; value: number; color: string }[]>();

  total(): number {
    return this.data().reduce((sum, s) => sum + s.value, 0);
  }

  /** Builds stacked dash arrays: each slice starts where the previous ended. */
  arcs(): { label: string; color: string; dash: string; offset: number }[] {
    const total = this.total() || 1;
    let consumed = 0;
    return this.data().filter(s => s.value > 0).map(slice => {
      const percent = (slice.value / total) * 100;
      const arc = { label: slice.label, color: slice.color,
                    dash: `${percent} ${100 - percent}`, offset: -consumed };
      consumed += percent;
      return arc;
    });
  }
}
