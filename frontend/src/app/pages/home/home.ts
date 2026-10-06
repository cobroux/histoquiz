import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { JoinResult, Period, PeriodInfo } from '../../core/models';
import { SessionStore } from '../../core/session.store';
import { ThemeToggle } from '../../core/theme-toggle';
import { PERIOD_COLORS, PERIOD_SHORT } from '../../core/ui';

type Mode = 'solo' | 'create' | 'join';

@Component({
  selector: 'app-home',
  imports: [FormsModule, ThemeToggle],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {
  private readonly api = inject(ApiService);
  private readonly sessions = inject(SessionStore);
  private readonly router = inject(Router);
  private readonly nameInput = viewChild<ElementRef<HTMLInputElement>>('nameInput');

  protected readonly questionCounts = [5, 10, 15, 20];
  protected readonly durations = [10, 20, 30];
  protected readonly periodColors = PERIOD_COLORS;
  protected readonly periodShort = PERIOD_SHORT;

  /** null = the start screen with the three mode cards. */
  protected readonly mode = signal<Mode | null>(null);
  protected readonly name = signal(this.sessions.name);
  protected readonly code = signal('');
  protected readonly periods = signal<PeriodInfo[]>([]);
  protected readonly selected = signal<Set<Period>>(new Set());
  protected readonly questionCount = signal(10);
  protected readonly seconds = signal(20);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly totalQuestions = computed(() =>
    this.periods().reduce((sum, p) => sum + p.questionCount, 0),
  );
  protected readonly available = computed(() => {
    const selected = this.selected();
    return this.periods()
      .filter((p) => selected.size === 0 || selected.has(p.id))
      .reduce((sum, p) => sum + p.questionCount, 0);
  });
  protected readonly canSubmit = computed(
    () => !this.busy() && (this.mode() !== 'join' || this.code().length === 5),
  );

  constructor() {
    this.api.periods().subscribe({
      next: (periods) => this.periods.set(periods),
      error: () => this.error.set('Serveur injoignable. Vérifie ta connexion.'),
    });
  }

  protected choose(mode: Mode): void {
    if (!this.name().trim()) {
      this.error.set("Choisis d'abord un pseudo !");
      this.nameInput()?.nativeElement.focus();
      return;
    }
    this.sessions.name = this.name().trim();
    this.error.set(null);
    this.mode.set(mode);
  }

  protected back(): void {
    this.mode.set(null);
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

  protected isSelected(id: Period): boolean {
    const selected = this.selected();
    return selected.size === 0 || selected.has(id);
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
    const mode = this.mode();
    if (!mode || !this.canSubmit()) {
      return;
    }
    const name = this.name().trim();
    this.busy.set(true);
    this.error.set(null);

    const request$: Observable<JoinResult> =
      mode === 'join'
        ? this.api.joinRoom(this.code(), name)
        : this.api.createRoom({
            name,
            periods: [...this.selected()],
            questionCount: Math.min(this.questionCount(), this.available()),
            secondsPerQuestion: this.seconds(),
            solo: mode === 'solo',
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
