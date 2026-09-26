import { Injectable, computed, signal } from '@angular/core';

/**
 * Mock persistence: users, session and per-user data live in localStorage.
 * Swap this service for real API calls once the backend has users and lists.
 */

export interface LibraryItem {
  id: string;
  name: string;
  category: string;
  weightGrams: number | null;
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

interface UserData {
  library: LibraryItem[];
  lists: PackList[];
}

interface StoredUser {
  username: string;
  passwordHash: string;
}

const USERS_KEY = 'packbase.users';
const SESSION_KEY = 'packbase.session';
const dataKey = (user: string) => `packbase.data.${user}`;

function read<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

function write(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Storage unavailable or full: the app keeps working for this session.
  }
}

function loadData(user: string | null): UserData {
  return user ? read<UserData>(dataKey(user), { library: [], lists: [] }) : { library: [], lists: [] };
}

async function sha256(text: string): Promise<string> {
  const bytes = new TextEncoder().encode(text);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
}

export function formatWeight(grams: number): string {
  if (grams < 1000) return `${grams} g`;
  return `${parseFloat((grams / 1000).toFixed(2))} kg`;
}

@Injectable({ providedIn: 'root' })
export class PackStore {
  private readonly _user = signal<string | null>(read<string | null>(SESSION_KEY, null));
  private readonly _data = signal<UserData>(loadData(this._user()));

  readonly user = this._user.asReadonly();
  readonly library = computed(() => this._data().library);
  readonly lists = computed(() => this._data().lists);

  // ---- auth ----

  async register(username: string, password: string): Promise<string | null> {
    const name = username.trim();
    if (name.length < 3) return 'Username must be at least 3 characters';
    if (password.length < 6) return 'Password must be at least 6 characters';
    const users = read<StoredUser[]>(USERS_KEY, []);
    if (users.some((u) => u.username.toLowerCase() === name.toLowerCase())) {
      return 'That username is taken';
    }
    users.push({ username: name, passwordHash: await sha256(password) });
    write(USERS_KEY, users);
    this.startSession(name);
    return null;
  }

  async login(username: string, password: string): Promise<string | null> {
    const name = username.trim().toLowerCase();
    const user = read<StoredUser[]>(USERS_KEY, []).find((u) => u.username.toLowerCase() === name);
    if (!user || user.passwordHash !== (await sha256(password))) {
      return 'Wrong username or password';
    }
    this.startSession(user.username);
    return null;
  }

  logout(): void {
    try {
      localStorage.removeItem(SESSION_KEY);
    } catch {
      // ignore
    }
    this._user.set(null);
    this._data.set({ library: [], lists: [] });
  }

  // ---- lists ----

  createList(name: string): string {
    const id = crypto.randomUUID();
    this.mutate((d) => ({
      ...d,
      lists: [{ id, name: name.trim(), createdAt: new Date().toISOString(), entries: [] }, ...d.lists],
    }));
    return id;
  }

  renameList(id: string, name: string): void {
    const trimmed = name.trim();
    if (!trimmed) return;
    this.updateList(id, (l) => ({ ...l, name: trimmed }));
  }

  deleteList(id: string): void {
    this.mutate((d) => ({ ...d, lists: d.lists.filter((l) => l.id !== id) }));
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
    this.updateList(listId, (l) => ({
      ...l,
      entries: l.entries.map((e) => (e.itemId === itemId ? { ...e, quantity } : e)),
    }));
  }

  removeEntry(listId: string, itemId: string): void {
    this.updateList(listId, (l) => ({ ...l, entries: l.entries.filter((e) => e.itemId !== itemId) }));
  }

  // ---- library ----

  /** Every new item lands in the library; optionally it is also put on a list. */
  createItem(input: NewItem, listId?: string): void {
    const item: LibraryItem = {
      id: crypto.randomUUID(),
      name: input.name.trim(),
      category: input.category.trim(),
      weightGrams: input.weightGrams,
    };
    this.mutate((d) => ({ ...d, library: [...d.library, item] }));
    if (listId) this.addEntry(listId, item.id);
  }

  /** Renames the library item; every list entry references it, so all lists follow. Empty names are ignored. */
  renameItem(itemId: string, name: string): void {
    const trimmed = name.trim();
    if (!trimmed) return;
    this.mutate((d) => ({ ...d, library: d.library.map((i) => (i.id === itemId ? { ...i, name: trimmed } : i)) }));
  }

  /** True if another library item (not `exceptId`) already has this name, ignoring case. */
  hasItemNamed(name: string, exceptId: string): boolean {
    const n = name.trim().toLowerCase();
    return this.library().some((i) => i.id !== exceptId && i.name.toLowerCase() === n);
  }

  /** Removes the item from the library and from every list that uses it. */
  deleteItem(itemId: string): void {
    this.mutate((d) => ({
      library: d.library.filter((i) => i.id !== itemId),
      lists: d.lists.map((l) => ({ ...l, entries: l.entries.filter((e) => e.itemId !== itemId) })),
    }));
  }

  // ---- internals ----

  private startSession(username: string): void {
    write(SESSION_KEY, username);
    this._user.set(username);
    this._data.set(loadData(username));
  }

  private updateList(id: string, fn: (list: PackList) => PackList): void {
    this.mutate((d) => ({ ...d, lists: d.lists.map((l) => (l.id === id ? fn(l) : l)) }));
  }

  private mutate(fn: (data: UserData) => UserData): void {
    const user = this._user();
    if (!user) return;
    const next = fn(this._data());
    this._data.set(next);
    write(dataKey(user), next);
  }
}
