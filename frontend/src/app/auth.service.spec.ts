import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API } from './api';
import { AuthService, MIN_PASSWORD_LENGTH } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('restore() sets the user when a session exists', async () => {
    const done = auth.restore();
    http.expectOne(`${API}/auth/me`).flush({ id: '1', email: 'a@b.se' });
    await done;
    expect(auth.user()).toBe('a@b.se');
  });

  it('restore() leaves the user null on 401', async () => {
    const done = auth.restore();
    http.expectOne(`${API}/auth/me`).flush(null, { status: 401, statusText: 'Unauthorized' });
    await done;
    expect(auth.user()).toBeNull();
  });

  it('login() trims the email and stores the user', async () => {
    const done = auth.login('  a@b.se ', 'secret');
    const req = http.expectOne(`${API}/auth/login`);
    expect(req.request.body).toEqual({ email: 'a@b.se', password: 'secret' });
    req.flush({ id: '1', email: 'a@b.se' });
    expect(await done).toBeNull();
    expect(auth.user()).toBe('a@b.se');
  });

  it('login() returns a generic message for wrong credentials', async () => {
    const done = auth.login('a@b.se', 'nope');
    http.expectOne(`${API}/auth/login`).flush({ message: 'x' }, { status: 401, statusText: 'Unauthorized' });
    expect(await done).toBe('Wrong email or password');
    expect(auth.user()).toBeNull();
  });

  it('register() rejects short passwords without calling the server', async () => {
    const message = await auth.register('a@b.se', 'x'.repeat(MIN_PASSWORD_LENGTH - 1));
    expect(message).toContain(String(MIN_PASSWORD_LENGTH));
    http.expectNone(`${API}/auth/register`);
  });

  it('register() reports a duplicate email on 409', async () => {
    const done = auth.register('a@b.se', 'x'.repeat(MIN_PASSWORD_LENGTH));
    http.expectOne(`${API}/auth/register`).flush({ message: 'x' }, { status: 409, statusText: 'Conflict' });
    expect(await done).toBe('That email is already registered');
  });

  it('register() logs in after creating the account', async () => {
    const password = 'x'.repeat(MIN_PASSWORD_LENGTH);
    const done = auth.register('a@b.se', password);
    http.expectOne(`${API}/auth/register`).flush(null, { status: 201, statusText: 'Created' });
    await vi.waitFor(() => http.expectOne(`${API}/auth/login`).flush({ id: '1', email: 'a@b.se' }));
    expect(await done).toBeNull();
    expect(auth.user()).toBe('a@b.se');
  });

  it('logout() clears the user even if the request fails', async () => {
    const login = auth.login('a@b.se', 'secret');
    http.expectOne(`${API}/auth/login`).flush({ id: '1', email: 'a@b.se' });
    await login;

    const done = auth.logout();
    http.expectOne(`${API}/auth/logout`).flush(null, { status: 500, statusText: 'Server Error' });
    await done;
    expect(auth.user()).toBeNull();
  });
});
