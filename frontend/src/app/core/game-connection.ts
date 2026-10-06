import { Client } from '@stomp/stompjs';
import { signal } from '@angular/core';
import { JoinResult, RoomView } from './models';

/**
 * Live link to one room: receives every state snapshot from the server and
 * sends the player's actions. Reconnects automatically (useful on mobile).
 */
export class GameConnection {
  readonly room = signal<RoomView | null>(null);
  readonly connected = signal(false);
  /** Server clock minus local clock, to display an accurate countdown. */
  private clockOffset = 0;
  private readonly client: Client;

  constructor(
    private readonly session: JoinResult,
    initial: RoomView | null,
  ) {
    if (initial) {
      this.apply(initial);
    }
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    this.client = new Client({
      brokerURL: `${protocol}://${location.host}/ws`,
      reconnectDelay: 2000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });
    this.client.onConnect = () => {
      this.connected.set(true);
      this.client.subscribe(`/topic/rooms/${session.code}`, (message) =>
        this.apply(JSON.parse(message.body) as RoomView),
      );
      this.send('hello');
    };
    this.client.onWebSocketClose = () => this.connected.set(false);
    this.client.activate();
  }

  get playerId(): string {
    return this.session.playerId;
  }

  serverNow(): number {
    return Date.now() + this.clockOffset;
  }

  start(): void {
    this.send('start');
  }

  answer(questionIndex: number, choice: number): void {
    this.send('answer', { questionIndex, choice });
  }

  next(): void {
    this.send('next');
  }

  restart(): void {
    this.send('restart');
  }

  leave(): void {
    this.send('leave');
  }

  disconnect(): void {
    void this.client.deactivate();
  }

  private send(action: string, body: Record<string, unknown> = {}): void {
    if (!this.client.connected) {
      return;
    }
    this.client.publish({
      destination: `/app/rooms/${this.session.code}/${action}`,
      body: JSON.stringify({ token: this.session.token, ...body }),
    });
  }

  private apply(view: RoomView): void {
    this.clockOffset = view.serverTime - Date.now();
    this.room.set(view);
  }
}
