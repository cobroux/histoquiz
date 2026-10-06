import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { CreateRoomRequest, JoinResult, PeriodInfo, RoomView } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  periods(): Observable<PeriodInfo[]> {
    return this.http.get<PeriodInfo[]>('/api/periods');
  }

  createRoom(request: CreateRoomRequest): Observable<JoinResult> {
    return this.http.post<JoinResult>('/api/rooms', request).pipe(catchError(toMessage));
  }

  joinRoom(code: string, name: string): Observable<JoinResult> {
    return this.http
      .post<JoinResult>(`/api/rooms/${encodeURIComponent(code)}/players`, { name })
      .pipe(catchError(toMessage));
  }

  room(code: string): Observable<RoomView> {
    return this.http
      .get<RoomView>(`/api/rooms/${encodeURIComponent(code)}`)
      .pipe(catchError(toMessage));
  }
}

/** Turns a ProblemDetail response into an Error carrying a readable French message. */
function toMessage(error: HttpErrorResponse): Observable<never> {
  const detail = error.error?.detail as string | undefined;
  const fallback =
    error.status === 0 ? 'Serveur injoignable. Vérifie ta connexion.' : 'Une erreur est survenue.';
  return throwError(() => new Error(detail ?? fallback));
}
