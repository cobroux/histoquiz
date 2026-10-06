import { Injectable } from '@angular/core';
import { JoinResult } from './models';

const NAME_KEY = 'histoquiz.name';
const SESSION_KEY = 'histoquiz.session.';

/** Remembers who we are in each room, so a refresh or a lost connection does not kick us out. */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  get name(): string {
    return read(NAME_KEY) ?? '';
  }

  set name(value: string) {
    write(NAME_KEY, value);
  }

  get(code: string): JoinResult | null {
    const raw = read(SESSION_KEY + code.toUpperCase());
    try {
      return raw ? (JSON.parse(raw) as JoinResult) : null;
    } catch {
      return null;
    }
  }

  save(session: JoinResult): void {
    write(SESSION_KEY + session.code.toUpperCase(), JSON.stringify(session));
  }

  clear(code: string): void {
    try {
      localStorage.removeItem(SESSION_KEY + code.toUpperCase());
    } catch {
      // Storage unavailable (private mode): nothing to clear.
    }
  }
}

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Storage unavailable: the game still works, it just won't survive a refresh.
  }
}
