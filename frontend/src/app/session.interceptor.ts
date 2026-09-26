import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** If the session expires while the app is open, drop back to the login page. */
export const sessionInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return next(req).pipe(
    catchError((error: unknown) => {
      const expired =
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !req.url.includes('/auth/') && // login failures and the startup check are handled by AuthService
        auth.user() !== null;
      if (expired) {
        auth.expire();
        void router.navigateByUrl('/login');
      }
      return throwError(() => error);
    }),
  );
};
