import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API, UserProfileDto, apiErrorMessage } from './api';

export const MIN_PASSWORD_LENGTH = 12;

/**
 * Session-cookie authentication. The browser holds the HttpOnly session cookie; this
 * service only tracks who is logged in. The CSRF header is added by Angular's HttpClient.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly _user = signal<string | null>(null);

  /** Email of the logged-in user, or null. */
  readonly user = this._user.asReadonly();

  /** Called once at startup: is there still a valid session? */
  async restore(): Promise<void> {
    try {
      const me = await firstValueFrom(this.http.get<UserProfileDto>(`${API}/auth/me`));
      this._user.set(me.email);
    } catch {
      this._user.set(null);
    }
  }

  /** Returns an error message, or null on success. */
  async login(email: string, password: string): Promise<string | null> {
    try {
      const me = await firstValueFrom(
        this.http.post<UserProfileDto>(`${API}/auth/login`, { email: email.trim(), password }),
      );
      this._user.set(me.email);
      return null;
    } catch (e) {
      if (e instanceof HttpErrorResponse && (e.status === 401 || e.status === 400)) {
        return 'Wrong email or password';
      }
      return apiErrorMessage(e, 'Could not log in. Please try again.');
    }
  }

  /** Creates the account and logs in. Returns an error message, or null on success. */
  async register(email: string, password: string): Promise<string | null> {
    if (password.length < MIN_PASSWORD_LENGTH) {
      return `Password must be at least ${MIN_PASSWORD_LENGTH} characters`;
    }
    try {
      await firstValueFrom(this.http.post(`${API}/auth/register`, { email: email.trim(), password }));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 409) return 'That email is already registered';
      return apiErrorMessage(e, 'Could not create the account. Please try again.');
    }
    return this.login(email, password);
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post(`${API}/auth/logout`, null));
    } catch {
      // The local state is cleared either way; an expired session is already logged out.
    } finally {
      this._user.set(null);
    }
  }

  /** The server says the session is gone (401 on a normal request). */
  expire(): void {
    this._user.set(null);
  }
}
