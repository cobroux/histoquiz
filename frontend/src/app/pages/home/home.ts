import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { JoinResult, Period, PeriodInfo } from '../../core/models';
import { SessionStore } from '../../core/session.store';

type Mode = 'solo' | 'create' | 'join';

@Component({
  selector: 'app-home',
  imports: [FormsModule],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {
  private readonly api = inject(ApiService);
  private readonly sessions = inject(SessionStore);
  private readonly router = inject(Router);

  protected readonly questionCounts = [5, 10, 15, 20];
  protected readonly durations = [10, 20, 30];

  protected readonly mode = signal<Mode>('solo');
  protected readonly name = signal(this.sessions.name);
  protected readonly code = signal('');
  protected readonly periods = signal<PeriodInfo[]>([]);
  protected readonly selected = signal<Set<Period>>(new Set());
  protected readonly questionCount = signal(10);
  protected readonly seconds = signal(20);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly available = computed(() => {
    const selected = this.selected();
    return this.periods()
      .filter((p) => selected.size === 0 || selected.has(p.id))
      .reduce((sum, p) => sum + p.questionCount, 0);
  });

  protected readonly canSubmit = computed(() => {
    if (this.busy() || !this.name().trim()) {
      return false;
    }
    return this.mode() !== 'join' || this.code().trim().length === 5;
  });

  constructor() {
    this.api.periods().subscribe({
      next: (periods) => this.periods.set(periods),
      error: () => this.error.set('Serveur injoignable. Vérifie ta connexion.'),
    });
  }

  protected setMode(mode: Mode): void {
    this.mode.set(mode);
    this.error.set(null);
  }

  protected togglePeriod(id: Period): void {
    const next = new Set(this.selected());
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    this.selected.set(next);
  }

  protected onCodeInput(value: string): void {
    this.code.set(
      value
        .toUpperCase()
        .replace(/[^A-Z0-9]/g, '')
        .slice(0, 5),
    );
  }

  protected submit(): void {
    if (!this.canSubmit()) {
      return;
    }
    const name = this.name().trim();
    this.sessions.name = name;
    this.busy.set(true);
    this.error.set(null);

    const request$: Observable<JoinResult> =
      this.mode() === 'join'
        ? this.api.joinRoom(this.code(), name)
        : this.api.createRoom({
            name,
            periods: [...this.selected()],
            questionCount: this.questionCount(),
            secondsPerQuestion: this.seconds(),
            solo: this.mode() === 'solo',
          });

    request$.subscribe({
      next: (session) => {
        this.sessions.save(session);
        void this.router.navigate(['/salon', session.code]);
      },
      error: (e: Error) => {
        this.error.set(e.message);
        this.busy.set(false);
      },
    });
  }
}
