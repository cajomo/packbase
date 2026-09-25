import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { PackStore } from './store.service';

@Component({
  selector: 'app-login-page',
  imports: [FormsModule],
  template: `
    <section class="card auth">
      <h1>{{ register() ? 'Create your account' : 'Welcome back' }}</h1>
      <p class="muted">
        {{ register() ? 'Start planning your next trip.' : 'Log in to see your packing lists.' }}
      </p>

      <form (ngSubmit)="submit()">
        <label>Username
          <input name="username" [(ngModel)]="username" autocomplete="username" required />
        </label>
        <label>Password
          <input
            name="password"
            type="password"
            [(ngModel)]="password"
            [autocomplete]="register() ? 'new-password' : 'current-password'"
            required
          />
        </label>
        @if (error(); as message) {
          <p class="error" role="alert">{{ message }}</p>
        }
        <button class="btn" type="submit" [disabled]="busy()">
          {{ register() ? 'Sign up' : 'Log in' }}
        </button>
      </form>

      <p class="switch muted">
        {{ register() ? 'Already have an account?' : 'New here?' }}
        <button type="button" class="link" (click)="toggle()">
          {{ register() ? 'Log in' : 'Create an account' }}
        </button>
      </p>
    </section>
  `,
  styles: `
    .auth { max-width: 400px; margin: 8vh auto 0; padding: 2rem; display: grid; gap: 0.5rem; }
    form { display: grid; gap: 1rem; margin-top: 1rem; }
    label { display: grid; gap: 0.3rem; font-size: 0.85rem; font-weight: 600; }
    .error { color: var(--danger); font-size: 0.9rem; }
    .switch { margin-top: 1rem; text-align: center; font-size: 0.9rem; }
    .link { font: inherit; font-weight: 600; color: var(--accent); background: none; border: 0; cursor: pointer; padding: 0; }
    .link:hover { text-decoration: underline; }
  `,
})
export class LoginPage {
  private readonly store = inject(PackStore);
  private readonly router = inject(Router);

  protected readonly register = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected username = '';
  protected password = '';

  protected toggle(): void {
    this.register.update((r) => !r);
    this.error.set(null);
  }

  protected async submit(): Promise<void> {
    this.busy.set(true);
    const error = this.register()
      ? await this.store.register(this.username, this.password)
      : await this.store.login(this.username, this.password);
    this.busy.set(false);
    if (error) {
      this.error.set(error);
    } else {
      this.router.navigateByUrl('/lists');
    }
  }
}
