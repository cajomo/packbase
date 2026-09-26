import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { AuthService } from './auth.service';
import { ListPage } from './list-page';
import { ListsPage } from './lists-page';
import { LoginPage } from './login-page';

const requireAuth: CanActivateFn = () =>
  inject(AuthService).user() ? true : inject(Router).createUrlTree(['/login']);

const guestOnly: CanActivateFn = () =>
  inject(AuthService).user() ? inject(Router).createUrlTree(['/lists']) : true;

export const routes: Routes = [
  { path: 'login', component: LoginPage, canActivate: [guestOnly] },
  { path: 'lists', component: ListsPage, canActivate: [requireAuth] },
  { path: 'lists/:id', component: ListPage, canActivate: [requireAuth] },
  { path: '', pathMatch: 'full', redirectTo: 'lists' },
  { path: '**', redirectTo: 'lists' },
];
