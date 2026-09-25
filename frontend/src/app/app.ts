import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { PackStore } from './store.service';

@Component({
  imports: [RouterOutlet, RouterLink],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly store = inject(PackStore);
  private readonly router = inject(Router);

  protected logout(): void {
    this.store.logout();
    this.router.navigateByUrl('/login');
  }
}
