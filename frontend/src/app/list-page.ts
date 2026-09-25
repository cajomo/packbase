import { Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LibraryItem, PackStore, formatWeight } from './store.service';

interface Row {
  item: LibraryItem;
  quantity: number;
}

interface Group {
  category: string;
  rows: Row[];
  weightGrams: number;
}

@Component({
  selector: 'app-list-page',
  imports: [FormsModule, RouterLink],
  styleUrl: './list-page.css',
  templateUrl: './list-page.html',
})
export class ListPage {
  protected readonly store = inject(PackStore);
  protected readonly fmt = formatWeight;

  /** Route param, bound via withComponentInputBinding(). */
  readonly id = input.required<string>();

  protected readonly list = computed(() => this.store.lists().find((l) => l.id === this.id()));

  protected readonly rows = computed<Row[]>(() => {
    const byId = new Map(this.store.library().map((i) => [i.id, i]));
    return (this.list()?.entries ?? []).flatMap((e) => {
      const item = byId.get(e.itemId);
      return item ? [{ item, quantity: e.quantity }] : [];
    });
  });

  protected readonly groups = computed<Group[]>(() => {
    const map = new Map<string, Row[]>();
    for (const row of this.rows()) {
      const key = row.item.category || 'Uncategorized';
      map.set(key, [...(map.get(key) ?? []), row]);
    }
    return [...map.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([category, rows]) => ({
        category,
        rows,
        weightGrams: rows.reduce((s, r) => s + (r.item.weightGrams ?? 0) * r.quantity, 0),
      }));
  });

  protected readonly totalItems = computed(() => this.rows().reduce((s, r) => s + r.quantity, 0));
  protected readonly totalWeight = computed(() => this.groups().reduce((s, g) => s + g.weightGrams, 0));

  protected readonly quantities = computed(
    () => new Map((this.list()?.entries ?? []).map((e) => [e.itemId, e.quantity])),
  );

  protected readonly categories = computed(() => [
    ...new Set(this.store.library().map((i) => i.category).filter(Boolean)),
  ]);

  protected readonly query = signal('');
  protected readonly library = computed(() => {
    const q = this.query().trim().toLowerCase();
    return this.store
      .library()
      .filter((i) => !q || i.name.toLowerCase().includes(q) || i.category.toLowerCase().includes(q));
  });

  protected readonly dragOver = signal(false);

  protected newName = '';
  protected newCategory = '';
  protected newWeight: number | null = null;

  protected rename(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.store.renameList(this.id(), input.value);
    input.value = this.list()?.name ?? input.value;
  }

  protected add(item: LibraryItem): void {
    this.store.addEntry(this.id(), item.id);
  }

  protected createItem(): void {
    if (!this.newName.trim()) return;
    this.store.createItem(
      { name: this.newName, category: this.newCategory, weightGrams: this.newWeight },
      this.id(),
    );
    this.newName = '';
    this.newWeight = null;
  }

  protected deleteFromLibrary(item: LibraryItem): void {
    if (confirm(`Delete "${item.name}" from your library? It is also removed from every list.`)) {
      this.store.deleteItem(item.id);
    }
  }

  // ---- drag and drop (native HTML5) ----

  protected onDragStart(event: DragEvent, item: LibraryItem): void {
    event.dataTransfer?.setData('text/plain', item.id);
    if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy';
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy';
    this.dragOver.set(true);
  }

  protected onDragLeave(event: DragEvent): void {
    const zone = event.currentTarget as HTMLElement;
    if (!zone.contains(event.relatedTarget as Node | null)) this.dragOver.set(false);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    const itemId = event.dataTransfer?.getData('text/plain');
    if (itemId && this.store.library().some((i) => i.id === itemId)) {
      this.store.addEntry(this.id(), itemId);
    }
  }
}
