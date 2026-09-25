import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { ListPage } from './list-page';
import { ListsPage } from './lists-page';
import { LoginPage } from './login-page';
import { PackStore } from './store.service';

const requireAuth: CanActivateFn = () =>
  inject(PackStore).user() ? true : inject(Router).createUrlTree(['/login']);

const guestOnly: CanActivateFn = () =>
  inject(PackStore).user() ? inject(Router).createUrlTree(['/lists']) : true;

export const routes: Routes = [
  { path: 'login', component: LoginPage, canActivate: [guestOnly] },
  { path: 'lists', component: ListsPage, canActivate: [requireAuth] },
  { path: 'lists/:id', component: ListPage, canActivate: [requireAuth] },
  { path: '', pathMatch: 'full', redirectTo: 'lists' },
  { path: '**', redirectTo: 'lists' },
];
