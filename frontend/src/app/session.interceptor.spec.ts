import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { API } from './api';
import { AuthService } from './auth.service';
import { sessionInterceptor } from './session.interceptor';

describe('sessionInterceptor', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let auth: AuthService;
  const navigateByUrl = vi.fn().mockResolvedValue(true);

  beforeEach(() => {
    navigateByUrl.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sessionInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigateByUrl } },
      ],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => ctrl.verify());

  async function logIn(): Promise<void> {
    const done = auth.login('a@b.se', 'secret');
    ctrl.expectOne(`${API}/auth/login`).flush({ id: '1', email: 'a@b.se' });
    await done;
  }

  function get401(url: string): void {
    http.get(url).subscribe({ error: () => undefined });
    ctrl.expectOne(url).flush(null, { status: 401, statusText: 'Unauthorized' });
  }

  it('logs out and goes to /login when a normal request returns 401', async () => {
    await logIn();
    get401(`${API}/gear`);
    expect(auth.user()).toBeNull();
    expect(navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('ignores 401 from /auth/ endpoints', async () => {
    await logIn();
    get401(`${API}/auth/me`);
    expect(auth.user()).toBe('a@b.se');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('does nothing when nobody is logged in', () => {
    get401(`${API}/gear`);
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('does not react to other errors', async () => {
    await logIn();
    http.get(`${API}/gear`).subscribe({ error: () => undefined });
    ctrl.expectOne(`${API}/gear`).flush(null, { status: 500, statusText: 'Server Error' });
    expect(auth.user()).toBe('a@b.se');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
