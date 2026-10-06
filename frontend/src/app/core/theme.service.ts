import { DOCUMENT, Injectable, inject, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const KEY = 'histoquiz.theme';

/**
 * Light/dark theme. Follows the device setting until the player picks one
 * with the toggle, then remembers that choice on this device.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly media = this.document.defaultView?.matchMedia('(prefers-color-scheme: dark)');
  private readonly stored = signal<Theme | null>(readStored());

  readonly theme = signal<Theme>(this.stored() ?? (this.media?.matches ? 'dark' : 'light'));

  constructor() {
    this.media?.addEventListener('change', (e) => {
      if (!this.stored()) {
        this.apply(e.matches ? 'dark' : 'light');
      }
    });
    this.apply(this.theme());
  }

  toggle(): void {
    const next: Theme = this.theme() === 'dark' ? 'light' : 'dark';
    this.stored.set(next);
    try {
      localStorage.setItem(KEY, next);
    } catch {
      // Storage unavailable: the choice lasts for this visit only.
    }
    this.apply(next);
  }

  private apply(theme: Theme): void {
    this.theme.set(theme);
    const root = this.document.documentElement;
    root.dataset['theme'] = theme;
    this.document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute('content', theme === 'dark' ? '#15132b' : '#f4f1ff');
  }
}

function readStored(): Theme | null {
  try {
    const value = localStorage.getItem(KEY);
    return value === 'light' || value === 'dark' ? value : null;
  } catch {
    return null;
  }
}
