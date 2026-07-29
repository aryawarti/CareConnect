import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ProvidersService } from '../../core/providers/providers.service';
import { Doctor } from '../../core/providers/provider.models';

/**
 * The public landing page — the first thing a visitor (or a reviewer opening
 * the repo's screenshot) sees. Pulls the real doctor directory, which is a
 * public endpoint, so the page is never empty once the clinic is seeded.
 */
@Component({
  selector: 'cc-welcome',
  standalone: true,
  imports: [CurrencyPipe, RouterLink, MatButtonModule, MatIconModule],
  template: `
    <section style="background:linear-gradient(160deg,#0f766e 0%,#115e59 55%,#134e4a 100%);
                    color:#fff;padding:76px 20px 84px">
      <div style="max-width:1080px;margin:0 auto;display:grid;
                  grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:44px;align-items:center">
        <div>
          <div style="display:inline-flex;align-items:center;gap:8px;background:rgba(255,255,255,.14);
                      padding:6px 14px;border-radius:999px;font-size:13px;margin-bottom:20px">
            <mat-icon style="font-size:17px;width:17px;height:17px">verified</mat-icon>
            Outpatient clinic platform
          </div>
          <h1 style="color:#fff;font-size:44px;line-height:1.12;margin-bottom:16px">
            Healthcare scheduling,<br>records and billing —<br>in one place.
          </h1>
          <p style="font-size:17px;line-height:1.6;color:rgba(255,255,255,.86);max-width:520px">
            Book with the right doctor in seconds. Your visit history, prescriptions and
            invoices stay together, and your care team sees exactly what it needs to —
            and nothing more.
          </p>
          <div class="cc-row" style="margin-top:30px;gap:12px">
            <a mat-flat-button routerLink="/register"
               style="background:#fff;color:var(--cc-primary-dark);padding:0 26px;height:46px">
              Create your account
            </a>
            <a mat-stroked-button routerLink="/doctors"
               style="color:#fff;border-color:rgba(255,255,255,.5);height:46px;padding:0 22px">
              Browse doctors
            </a>
          </div>
        </div>

        <div class="cc-grid cc-grid-2" style="gap:14px">
          @for (item of highlights; track item.title) {
            <div style="background:rgba(255,255,255,.10);border:1px solid rgba(255,255,255,.16);
                        border-radius:14px;padding:18px">
              <mat-icon style="color:#5eead4">{{ item.icon }}</mat-icon>
              <div style="font-weight:600;margin:8px 0 4px">{{ item.title }}</div>
              <div style="font-size:13px;color:rgba(255,255,255,.78);line-height:1.5">
                {{ item.text }}
              </div>
            </div>
          }
        </div>
      </div>
    </section>

    @if (doctors().length) {
      <section class="cc-page">
        <div class="cc-page-head">
          <div>
            <h2>Our doctors</h2>
            <div class="cc-sub">Specialists accepting appointments this week</div>
          </div>
          <span class="cc-spacer"></span>
          <a mat-stroked-button routerLink="/doctors">View all</a>
        </div>

        <div class="cc-grid cc-grid-3">
          @for (doc of doctors(); track doc.id) {
            <div class="cc-card">
              <div class="cc-row" style="gap:12px">
                <div class="cc-stat-icon"><mat-icon>stethoscope</mat-icon></div>
                <div>
                  <div style="font-weight:600">Dr. {{ doc.firstName }} {{ doc.lastName }}</div>
                  <div class="cc-faint">{{ doc.specialty }}</div>
                </div>
              </div>
              <div class="cc-divider"></div>
              <div class="cc-row">
                <span class="cc-faint">{{ doc.departmentName }}</span>
                <span class="cc-spacer" style="flex:1"></span>
                <span class="cc-money">{{ doc.consultationFee | currency:'INR':'symbol':'1.0-0' }}</span>
              </div>
            </div>
          }
        </div>
      </section>
    }
  `
})
export class WelcomeComponent {
  private readonly providers = inject(ProvidersService);

  readonly doctors = signal<Doctor[]>([]);

  readonly highlights = [
    { icon: 'event_available', title: 'Real-time booking',
      text: 'Only genuinely free slots are offered — double-booking is impossible.' },
    { icon: 'clinical_notes', title: 'Records that follow you',
      text: 'Notes, diagnoses and prescriptions from every visit, in one history.' },
    { icon: 'receipt_long', title: 'Transparent billing',
      text: 'Invoices are issued at the price you were quoted when booking.' },
    { icon: 'shield_lock', title: 'Private by design',
      text: 'Your chart is visible to you and your treating doctor — nobody else.' },
  ];

  constructor() {
    this.providers.directory('', 0, 6).subscribe(r => this.doctors.set(r.data));
  }
}
