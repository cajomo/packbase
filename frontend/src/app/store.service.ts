import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import {
  API,
  GearItemDto,
  GearItemInputDto,
  ListEntryDto,
  PackListDto,
  PackListInputDto,
  apiErrorMessage,
} from './api';
import { AuthService } from './auth.service';

/**
 * The user's library and lists, loaded from the backend after login. Changes are applied
 * to the local signals immediately (optimistic) and saved in the background; if a save
 * fails the error is shown and the server state is reloaded.
 */

export interface LibraryItem {
  id: string;
  name: string;
  category: string;
  weightGrams: number | null;
  /** Not editable in the UI yet; carried along so renaming does not wipe them on the server. */
  quantity?: number;
  notes?: string | null;
}

export interface ListEntry {
  itemId: string;
  quantity: number;
  /** Per-list category override; falls back to the library item's category. */
  category?: string;
}

export interface PackList {
  id: string;
  name: string;
  createdAt: string;
  entries: ListEntry[];
}

export interface NewItem {
  name: string;
  category: string;
  weightGrams: number | null;
}

const MAX_QUANTITY = 9999;

function toLibraryItem(dto: GearItemDto): LibraryItem {
  return {
    id: dto.id,
    name: dto.name,
    category: dto.category ?? '',
    weightGrams: dto.weightGrams ?? null,
    quantity: dto.quantity ?? undefined,
    notes: dto.notes,
  };
}

function toGearInput(item: Omit<LibraryItem, 'id'>): GearItemInputDto {
  const input: GearItemInputDto = { name: item.name, category: item.category };
  if (item.weightGrams !== null) input.weightGrams = item.weightGrams;
  if (item.quantity !== undefined) input.quantity = item.quantity;
  if (item.notes) input.notes = item.notes;
  return input;
}

function toPackList(dto: PackListDto): PackList {
  return {
    id: dto.id,
    name: dto.name,
    createdAt: dto.createdAt,
    entries: dto.entries.map((e) =>
      e.category == null
        ? { itemId: e.itemId, quantity: e.quantity }
        : { itemId: e.itemId, quantity: e.quantity, category: e.category },
    ),
  };
}

function toListInput(list: PackList): PackListInputDto {
  const entries: ListEntryDto[] = list.entries.map((e) =>
    e.category === undefined ? { itemId: e.itemId, quantity: e.quantity } : { ...e },
  );
  return { name: list.name, entries };
}

export function formatWeight(grams: number): string {
  if (grams < 1000) return `${grams} g`;
  return `${parseFloat((grams / 1000).toFixed(2))} kg`;
}

@Injectable({ providedIn: 'root' })
export class PackStore {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private readonly _library = signal<LibraryItem[]>([]);
  private readonly _lists = signal<PackList[]>([]);
  private readonly _loaded = signal(false);

  readonly library = this._library.asReadonly();
  readonly lists = this._lists.asReadonly();
  /** False until the first load after login has finished. */
  readonly loaded = this._loaded.asReadonly();
  /** Last failure to show the user, or null. */
  readonly error = signal<string | null>(null);

  /** Bumped on every load/clear so a slow response for a previous user is ignored. */
  private generation = 0;
  private readonly saving = new Set<string>();
  private readonly resave = new Set<string>();

  constructor() {
    effect(() => {
      const loggedIn = this.auth.user() !== null;
      untracked(() => (loggedIn ? void this.load() : this.clear()));
    });
  }

  async load(silent = false): Promise<void> {
    const generation = ++this.generation;
    if (!silent) this._loaded.set(false);
    try {
      const [items, lists] = await Promise.all([
        firstValueFrom(this.http.get<GearItemDto[]>(`${API}/gear`)),
        firstValueFrom(this.http.get<PackListDto[]>(`${API}/lists`)),
      ]);
      if (generation !== this.generation) return;
      this._library.set(items.map(toLibraryItem));
      this._lists.set(lists.map(toPackList));
    } catch (e) {
      if (generation === this.generation) this.error.set(apiErrorMessage(e, "Couldn't load your data."));
    } finally {
      if (generation === this.generation) this._loaded.set(true);
    }
  }

  private clear(): void {
    this.generation++;
    this._library.set([]);
    this._lists.set([]);
    this._loaded.set(false);
    this.error.set(null);
    this.saving.clear();
    this.resave.clear();
  }

  // ---- lists ----

  /** Returns the new list's id, or null if it could not be created. */
  async createList(name: string): Promise<string | null> {
    try {
      const dto = await firstValueFrom(
        this.http.post<PackListDto>(`${API}/lists`, { name: name.trim() } satisfies PackListInputDto),
      );
      this._lists.update((lists) => [toPackList(dto), ...lists]);
      return dto.id;
    } catch (e) {
      this.error.set(apiErrorMessage(e, "Couldn't create the list."));
      return null;
    }
  }

  renameList(id: string, name: string): void {
    const trimmed = name.trim();
    if (!trimmed) return;
    this.updateList(id, (l) => (l.name === trimmed ? l : { ...l, name: trimmed }));
  }

  deleteList(id: string): void {
    this._lists.update((lists) => lists.filter((l) => l.id !== id));
    this.saving.delete(id);
    this.resave.delete(id);
    void this.send(this.http.delete(`${API}/lists/${id}`), "Couldn't delete the list.");
  }

  listStats(list: PackList): { items: number; weightGrams: number } {
    const byId = new Map(this.library().map((i) => [i.id, i]));
    let items = 0;
    let weightGrams = 0;
    for (const e of list.entries) {
      items += e.quantity;
      weightGrams += (byId.get(e.itemId)?.weightGrams ?? 0) * e.quantity;
    }
    return { items, weightGrams };
  }

  // ---- entries ----

  /** Adds one of the library item to the list; does nothing if it is already there (change quantity in the list). */
  addEntry(listId: string, itemId: string, category?: string): void {
    this.updateList(listId, (l) => {
      if (l.entries.some((e) => e.itemId === itemId)) return l;
      const entry = category === undefined ? { itemId, quantity: 1 } : { itemId, quantity: 1, category };
      return { ...l, entries: [...l.entries, entry] };
    });
  }

  /**
   * Moves an entry into `category` ('' = uncategorized), placed before `beforeItemId`,
   * or last in the list (so last in its category) when that is null.
   */
  moveEntry(listId: string, itemId: string, category: string, beforeItemId: string | null): void {
    this.updateList(listId, (l) => {
      const entry = l.entries.find((e) => e.itemId === itemId);
      if (!entry) return l;
      const moved = { ...entry, category };
      if (beforeItemId === itemId) {
        return { ...l, entries: l.entries.map((e) => (e.itemId === itemId ? moved : e)) };
      }
      const rest = l.entries.filter((e) => e.itemId !== itemId);
      const idx = beforeItemId ? rest.findIndex((e) => e.itemId === beforeItemId) : -1;
      if (idx < 0) rest.push(moved);
      else rest.splice(idx, 0, moved);
      return { ...l, entries: rest };
    });
  }

  setQuantity(listId: string, itemId: string, quantity: number): void {
    if (quantity < 1) return this.removeEntry(listId, itemId);
    const clamped = Math.min(quantity, MAX_QUANTITY);
    this.updateList(listId, (l) => ({
      ...l,
      entries: l.entries.map((e) => (e.itemId === itemId ? { ...e, quantity: clamped } : e)),
    }));
  }

  removeEntry(listId: string, itemId: string): void {
    this.updateList(listId, (l) => ({ ...l, entries: l.entries.filter((e) => e.itemId !== itemId) }));
  }

  // ---- library ----

  /** Every new item lands in the library; optionally it is also put on a list. */
  async createItem(input: NewItem, listId?: string): Promise<void> {
    const body = toGearInput({
      name: input.name.trim(),
      category: input.category.trim(),
      weightGrams: input.weightGrams,
    });
    try {
      const dto = await firstValueFrom(this.http.post<GearItemDto>(`${API}/gear`, body));
      const item = toLibraryItem(dto);
      this._library.update((library) => [...library, item]);
      if (listId) this.addEntry(listId, item.id);
    } catch (e) {
      this.error.set(apiErrorMessage(e, "Couldn't create the item."));
    }
  }

  /** Renames the library item; every list entry references it, so all lists follow. Empty names are ignored. */
  renameItem(itemId: string, name: string): void {
    const trimmed = name.trim();
    const current = this.library().find((i) => i.id === itemId);
    if (!trimmed || !current) return;
    const renamed = { ...current, name: trimmed };
    this._library.update((library) => library.map((i) => (i.id === itemId ? renamed : i)));
    void this.send(this.http.put(`${API}/gear/${itemId}`, toGearInput(renamed)), "Couldn't rename the item.");
  }

  /** True if another library item (not `exceptId`) already has this name, ignoring case. */
  hasItemNamed(name: string, exceptId: string): boolean {
    const n = name.trim().toLowerCase();
    return this.library().some((i) => i.id !== exceptId && i.name.toLowerCase() === n);
  }

  /** Removes the item from the library and from every list that uses it. */
  deleteItem(itemId: string): void {
    this._library.update((library) => library.filter((i) => i.id !== itemId));
    this._lists.update((lists) =>
      lists.map((l) =>
        l.entries.some((e) => e.itemId === itemId) ? { ...l, entries: l.entries.filter((e) => e.itemId !== itemId) } : l,
      ),
    );
    void this.send(this.http.delete(`${API}/gear/${itemId}`), "Couldn't delete the item.");
  }

  // ---- internals ----

  private updateList(id: string, fn: (list: PackList) => PackList): void {
    let changed = false;
    this._lists.update((lists) =>
      lists.map((l) => {
        if (l.id !== id) return l;
        const next = fn(l);
        changed = next !== l;
        return next;
      }),
    );
    if (changed) void this.save(id);
  }

  /**
   * Saves the list's current state. Saves for one list never overlap: while one is in
   * flight, further changes only mark the list dirty and are sent together afterwards,
   * so an older request can never overwrite a newer one on the server.
   */
  private async save(id: string): Promise<void> {
    if (this.saving.has(id)) {
      this.resave.add(id);
      return;
    }
    this.saving.add(id);
    try {
      do {
        this.resave.delete(id);
        const list = this._lists().find((l) => l.id === id);
        if (!list) return;
        await firstValueFrom(this.http.put(`${API}/lists/${id}`, toListInput(list)));
      } while (this.resave.has(id));
    } catch (e) {
      this.resave.delete(id);
      this.fail(e, "Couldn't save your changes.");
    } finally {
      this.saving.delete(id);
    }
  }

  private async send(request: Observable<unknown>, message: string): Promise<void> {
    try {
      await firstValueFrom(request);
    } catch (e) {
      this.fail(e, message);
    }
  }

  private fail(error: unknown, message: string): void {
    this.error.set(apiErrorMessage(error, message));
    void this.load(true); // resync with what the server actually has
  }
}
