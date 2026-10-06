import {
  Component,
  DestroyRef,
  OnInit,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { GameConnection } from '../../core/game-connection';
import { PlayerView, RoomView } from '../../core/models';
import { SessionStore } from '../../core/session.store';

/** Delay before the server moves on after a reveal, in multiplayer (see RoomService.AUTO_ADVANCE). */
const AUTO_ADVANCE_SECONDS = 8;

@Component({
  selector: 'app-room',
  imports: [FormsModule, RouterLink],
  templateUrl: './room.html',
  styleUrl: './room.css',
})
export class RoomPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly sessions = inject(SessionStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** Room code, bound from the route. */
  readonly code = input.required<string>();

  protected readonly shapes = ['▲', '◆', '●', '■'];
  protected readonly autoAdvanceSeconds = AUTO_ADVANCE_SECONDS;

  protected readonly status = signal<'loading' | 'join' | 'playing' | 'error'>('loading');
  protected readonly error = signal<string | null>(null);
  protected readonly name = signal(this.sessions.name);
  protected readonly busy = signal(false);
  protected readonly shareFeedback = signal<string | null>(null);
  protected readonly connection = signal<GameConnection | null>(null);
  /** The answer picked for the current question, kept locally since the server only reveals counts. */
  protected readonly myAnswer = signal<{ questionIndex: number; choice: number } | null>(null);
  private readonly now = signal(Date.now());

  protected readonly room = computed<RoomView | null>(() => this.connection()?.room() ?? null);
  protected readonly online = computed(() => this.connection()?.connected() ?? false);
  protected readonly me = computed<PlayerView | null>(() => {
    const conn = this.connection();
    return this.room()?.players.find((p) => p.id === conn?.playerId) ?? null;
  });
  protected readonly isHost = computed(() => !!this.me() && this.room()?.hostId === this.me()?.id);
  protected readonly ranking = computed(() =>
    [...(this.room()?.players ?? [])].sort(
      (a, b) => b.score - a.score || a.name.localeCompare(b.name),
    ),
  );
  protected readonly myRank = computed(
    () => this.ranking().findIndex((p) => p.id === this.me()?.id) + 1,
  );
  protected readonly myChoice = computed(() => {
    const answer = this.myAnswer();
    return answer && answer.questionIndex === this.room()?.questionIndex ? answer.choice : null;
  });
  protected readonly answeredCount = computed(
    () => this.room()?.players.filter((p) => p.connected && p.answered).length ?? 0,
  );
  protected readonly connectedCount = computed(
    () => this.room()?.players.filter((p) => p.connected).length ?? 0,
  );
  protected readonly remainingMs = computed(() => {
    const room = this.room();
    const conn = this.connection();
    if (!room || !conn || room.phase !== 'QUESTION') {
      return 0;
    }
    this.now(); // re-evaluate on every tick
    return Math.max(0, room.deadline - conn.serverNow());
  });
  protected readonly remainingRatio = computed(() => {
    const total = (this.room()?.settings.secondsPerQuestion ?? 1) * 1000;
    return Math.min(1, this.remainingMs() / total);
  });
  protected readonly shareUrl = computed(
    () => `${location.origin}/salon/${this.room()?.code ?? ''}`,
  );

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 200);
    this.destroyRef.onDestroy(() => {
      clearInterval(timer);
      this.connection()?.disconnect();
    });

    // A solo game starts as soon as the player is connected (and again after "Rejouer").
    let autoStarted = false;
    effect(() => {
      const room = this.room();
      if (room?.phase !== 'LOBBY') {
        autoStarted = false;
        return;
      }
      if (room.solo && this.isHost() && this.online() && !autoStarted) {
        autoStarted = true;
        this.connection()?.start();
      }
    });
  }

  ngOnInit(): void {
    const code = this.code().toUpperCase();
    this.api.room(code).subscribe({
      next: (room) => {
        const session = this.sessions.get(code);
        if (session && room.players.some((p) => p.id === session.playerId)) {
          this.connection.set(new GameConnection(session, room));
          this.status.set('playing');
        } else {
          if (session) {
            this.sessions.clear(code);
          }
          if (room.phase !== 'LOBBY' || room.solo) {
            this.fail(room.solo ? 'Cette partie est en mode solo.' : 'La partie a déjà commencé.');
          } else {
            this.status.set('join');
          }
        }
      },
      error: (e: Error) => this.fail(e.message),
    });
  }

  protected join(): void {
    const name = this.name().trim();
    if (!name || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.sessions.name = name;
    this.api.joinRoom(this.code(), name).subscribe({
      next: (session) => {
        this.sessions.save(session);
        this.connection.set(new GameConnection(session, null));
        this.status.set('playing');
        this.busy.set(false);
      },
      error: (e: Error) => {
        this.error.set(e.message);
        this.busy.set(false);
      },
    });
  }

  protected choose(choice: number): void {
    const room = this.room();
    if (!room || room.phase !== 'QUESTION' || this.myChoice() !== null || this.remainingMs() <= 0) {
      return;
    }
    this.myAnswer.set({ questionIndex: room.questionIndex, choice });
    this.connection()?.answer(room.questionIndex, choice);
    navigator.vibrate?.(20);
  }

  protected start(): void {
    this.connection()?.start();
  }

  protected next(): void {
    this.connection()?.next();
  }

  protected restart(): void {
    this.connection()?.restart();
  }

  protected leave(): void {
    this.connection()?.leave();
    this.sessions.clear(this.code());
    void this.router.navigate(['/']);
  }

  protected async share(): Promise<void> {
    const url = this.shareUrl();
    const text = `Rejoins mon quiz d'histoire ! Code : ${this.room()?.code}`;
    try {
      if (navigator.share) {
        await navigator.share({ title: 'HistoQuiz', text, url });
        return;
      }
      await navigator.clipboard.writeText(url);
      this.flashShare('Lien copié !');
    } catch (e) {
      if ((e as DOMException)?.name !== 'AbortError') {
        this.flashShare('Copie impossible, partage le code.');
      }
    }
  }

  protected countFor(choice: number): number {
    return this.room()?.reveal?.answerCounts[choice] ?? 0;
  }

  protected initials(name: string): string {
    return name.trim().charAt(0).toUpperCase();
  }

  private flashShare(message: string): void {
    this.shareFeedback.set(message);
    setTimeout(() => this.shareFeedback.set(null), 2000);
  }

  private fail(message: string): void {
    this.error.set(message);
    this.status.set('error');
  }
}
