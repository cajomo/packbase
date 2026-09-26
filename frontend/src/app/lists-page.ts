import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PackList, PackStore, formatWeight } from './store.service';

@Component({
  selector: 'app-lists-page',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="head">
      <h1>Your lists</h1>
      <p class="muted">Plan a trip, then drag gear in from your library.</p>
    </div>

    <div class="grid">
      <form class="card new" (ngSubmit)="create()">
        <h3>New list</h3>
        <input name="name" [(ngModel)]="name" placeholder="e.g. Kungsleden, 5 days" maxlength="100" required />
        <button class="btn" type="submit" [disabled]="!name.trim() || creating()">Create list</button>
      </form>

      @for (list of store.lists(); track list.id) {
        <a class="card list" [routerLink]="['/lists', list.id]">
          <div class="top">
            <h3>{{ list.name }}</h3>
            <button
              type="button"
              class="icon-btn danger"
              [attr.aria-label]="'Delete ' + list.name"
              (click)="remove(list, $event)"
            >
              ✕
            </button>
          </div>
          <div class="meta">
            <span class="pill">{{ stats(list).items }} items</span>
            <span class="pill">{{ weight(list) }}</span>
          </div>
        </a>
      }
    </div>

    @if (!store.loaded()) {
      <p class="muted empty">Loading your lists…</p>
    } @else if (store.lists().length === 0) {
      <p class="muted empty">No lists yet. Create your first one above.</p>
    }
  `,
  styles: `
    .head { display: grid; gap: 0.25rem; margin-bottom: 1.5rem; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 1rem; }
    .new { display: grid; gap: 0.75rem; padding: 1.25rem; align-content: start; border-style: dashed; box-shadow: none; }
    .list {
      display: grid; gap: 1.25rem; padding: 1.25rem; color: inherit; text-decoration: none;
      transition: transform 0.15s, border-color 0.15s;
    }
    .list:hover { transform: translateY(-2px); border-color: var(--accent); }
    .top { display: flex; justify-content: space-between; align-items: start; gap: 0.5rem; }
    .meta { display: flex; gap: 0.5rem; flex-wrap: wrap; }
    .pill { padding: 0.15rem 0.65rem; border-radius: 999px; background: var(--accent-soft); color: var(--accent); font-size: 0.8rem; font-weight: 600; }
    .empty { margin-top: 2rem; text-align: center; }
  `,
})
export class ListsPage {
  protected readonly store = inject(PackStore);
  private readonly router = inject(Router);
  protected name = '';

  protected readonly creating = signal(false);

  protected async create(): Promise<void> {
    if (!this.name.trim() || this.creating()) return;
    this.creating.set(true);
    const id = await this.store.createList(this.name);
    this.creating.set(false);
    if (id) {
      this.name = '';
      this.router.navigate(['/lists', id]);
    }
  }

  protected stats(list: PackList) {
    return this.store.listStats(list);
  }

  protected weight(list: PackList): string {
    return formatWeight(this.stats(list).weightGrams);
  }

  protected remove(list: PackList, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    if (confirm(`Delete "${list.name}"? Your items stay in the library.`)) {
      this.store.deleteList(list.id);
    }
  }
}
