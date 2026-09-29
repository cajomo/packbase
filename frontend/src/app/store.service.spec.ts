import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { API, GearItemDto, PackListDto } from './api';
import { AuthService } from './auth.service';
import { PackStore, formatWeight } from './store.service';

const NOW = '2026-01-01T00:00:00Z';

const tent: GearItemDto = { id: 'tent', name: 'Tent', category: 'Shelter', weightGrams: 1200, createdAt: NOW };
const stove: GearItemDto = { id: 'stove', name: 'Stove', category: 'Cooking', weightGrams: 300, createdAt: NOW };

function list(overrides: Partial<PackListDto> = {}): PackListDto {
  return { id: 'l1', name: 'Weekend', createdAt: NOW, entries: [], ...overrides };
}

describe('PackStore', () => {
  let store: PackStore;
  let http: HttpTestingController;
  const user = signal<string | null>(null);

  beforeEach(() => {
    user.set(null);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { user: user.asReadonly() } },
      ],
    });
    store = TestBed.inject(PackStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Logs in and answers the initial load. */
  async function loadWith(items: GearItemDto[], lists: PackListDto[]): Promise<void> {
    user.set('a@b.se');
    TestBed.tick();
    http.expectOne(`${API}/gear`).flush(items);
    http.expectOne(`${API}/lists`).flush(lists);
    await vi.waitFor(() => expect(store.loaded()).toBe(true));
  }

  it('loads the library and lists after login', async () => {
    await loadWith([tent], [list({ entries: [{ itemId: 'tent', quantity: 2 }] })]);
    expect(store.library().map((i) => i.name)).toEqual(['Tent']);
    expect(store.lists()[0].entries).toEqual([{ itemId: 'tent', quantity: 2 }]);
  });

  it('clears everything on logout', async () => {
    await loadWith([tent], [list()]);
    user.set(null);
    TestBed.tick();
    expect(store.library()).toEqual([]);
    expect(store.lists()).toEqual([]);
    expect(store.loaded()).toBe(false);
  });

  it('adds an entry optimistically and saves the whole list', async () => {
    await loadWith([tent], [list()]);
    store.addEntry('l1', 'tent');
    expect(store.lists()[0].entries).toEqual([{ itemId: 'tent', quantity: 1 }]);

    const put = http.expectOne(`${API}/lists/l1`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ name: 'Weekend', entries: [{ itemId: 'tent', quantity: 1 }] });
    put.flush(list());
  });

  it('ignores adding an item that is already on the list', async () => {
    await loadWith([tent], [list({ entries: [{ itemId: 'tent', quantity: 1 }] })]);
    store.addEntry('l1', 'tent');
    http.expectNone(`${API}/lists/l1`);
  });

  it('removes the entry when the quantity drops below 1 and clamps large quantities', async () => {
    await loadWith([tent, stove], [list({ entries: [{ itemId: 'tent', quantity: 1 }, { itemId: 'stove', quantity: 1 }] })]);

    store.setQuantity('l1', 'tent', 0);
    expect(store.lists()[0].entries.map((e) => e.itemId)).toEqual(['stove']);
    http.expectOne(`${API}/lists/l1`).flush(list());
    await new Promise((resolve) => setTimeout(resolve)); // let the finished save release its lock

    store.setQuantity('l1', 'stove', 100000);
    expect(store.lists()[0].entries[0].quantity).toBe(9999);
    http.expectOne(`${API}/lists/l1`).flush(list());
  });

  it('moves an entry to another category, before a given item', async () => {
    const entries = [
      { itemId: 'tent', quantity: 1 },
      { itemId: 'stove', quantity: 1 },
    ];
    await loadWith([tent, stove], [list({ entries })]);

    store.moveEntry('l1', 'stove', 'Gear', 'tent');
    expect(store.lists()[0].entries).toEqual([
      { itemId: 'stove', quantity: 1, category: 'Gear' },
      { itemId: 'tent', quantity: 1 },
    ]);
    http.expectOne(`${API}/lists/l1`).flush(list());
  });

  it('never has two saves for the same list in flight; later changes are sent together', async () => {
    await loadWith([tent, stove], [list()]);

    store.addEntry('l1', 'tent');
    store.addEntry('l1', 'stove');
    store.setQuantity('l1', 'tent', 3);

    // Only the first save is on the wire.
    const first = http.expectOne(`${API}/lists/l1`);
    expect(first.request.body.entries).toEqual([{ itemId: 'tent', quantity: 1 }]);
    first.flush(list());

    // The rest follow as one request with the newest state.
    const second = await vi.waitFor(() => http.expectOne(`${API}/lists/l1`));
    expect(second.request.body.entries).toEqual([
      { itemId: 'tent', quantity: 3 },
      { itemId: 'stove', quantity: 1 },
    ]);
    second.flush(list());
  });

  it('shows an error and reloads from the server when a save fails', async () => {
    await loadWith([tent], [list()]);
    store.addEntry('l1', 'tent');
    http.expectOne(`${API}/lists/l1`).flush(null, { status: 500, statusText: 'Server Error' });

    await vi.waitFor(() => expect(store.error()).toBe("Couldn't save your changes."));
    http.expectOne(`${API}/gear`).flush([tent]);
    http.expectOne(`${API}/lists`).flush([list()]);
    await vi.waitFor(() => expect(store.lists()[0].entries).toEqual([]));
  });

  it('renames a list (trimmed) and ignores empty names', async () => {
    await loadWith([], [list()]);
    store.renameList('l1', '   ');
    http.expectNone(`${API}/lists/l1`);

    store.renameList('l1', '  Summer ');
    expect(store.lists()[0].name).toBe('Summer');
    http.expectOne(`${API}/lists/l1`).flush(list({ name: 'Summer' }));
  });

  it('deleting a library item also removes it from lists', async () => {
    await loadWith([tent], [list({ entries: [{ itemId: 'tent', quantity: 1 }] })]);
    store.deleteItem('tent');
    expect(store.library()).toEqual([]);
    expect(store.lists()[0].entries).toEqual([]);
    const del = http.expectOne(`${API}/gear/tent`);
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('createList puts the new list first and returns its id', async () => {
    await loadWith([], [list()]);
    const created = store.createList(' Trip ');
    const post = http.expectOne(`${API}/lists`);
    expect(post.request.body).toEqual({ name: 'Trip' });
    post.flush(list({ id: 'l2', name: 'Trip' }));
    expect(await created).toBe('l2');
    expect(store.lists().map((l) => l.id)).toEqual(['l2', 'l1']);
  });

  it('createItem can also put the item on a list', async () => {
    await loadWith([], [list()]);
    const done = store.createItem({ name: ' Tent ', category: ' Shelter ', weightGrams: 1200 }, 'l1');
    const post = http.expectOne(`${API}/gear`);
    expect(post.request.body).toEqual({ name: 'Tent', category: 'Shelter', weightGrams: 1200 });
    post.flush(tent);
    await done;
    expect(store.library().map((i) => i.id)).toEqual(['tent']);
    http.expectOne(`${API}/lists/l1`).flush(list());
  });

  it('listStats sums quantities and weights', async () => {
    await loadWith([tent, stove], [list({ entries: [{ itemId: 'tent', quantity: 1 }, { itemId: 'stove', quantity: 2 }] })]);
    expect(store.listStats(store.lists()[0])).toEqual({ items: 3, weightGrams: 1800 });
  });

  it('hasItemNamed ignores case and the item itself', async () => {
    await loadWith([tent, stove], []);
    expect(store.hasItemNamed(' tent ', 'stove')).toBe(true);
    expect(store.hasItemNamed('tent', 'tent')).toBe(false);
  });
});

describe('formatWeight', () => {
  it('uses grams below 1 kg and kilograms above', () => {
    expect(formatWeight(450)).toBe('450 g');
    expect(formatWeight(1000)).toBe('1 kg');
    expect(formatWeight(1234)).toBe('1.23 kg');
  });
});
