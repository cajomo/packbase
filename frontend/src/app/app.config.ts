import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { AuthService } from './auth.service';
import { sessionInterceptor } from './session.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // XSRF-TOKEN cookie -> X-XSRF-TOKEN header on POST/PUT/DELETE is Angular's default behaviour.
    provideHttpClient(withInterceptors([sessionInterceptor])),
    // Find out whether a session already exists before the first route (and its guard) runs.
    provideAppInitializer(() => inject(AuthService).restore()),
    provideRouter(routes, withComponentInputBinding()),
  ],
};
