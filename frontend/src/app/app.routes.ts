import { Routes } from '@angular/router';
import { frontRoutes } from './front/front.routes';
import { adminRoutes } from './admin/admin.routes';
import { authGuard } from './shared/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    children: [
      ...frontRoutes
    ]
  },
  {
    path: 'admin',
    canActivate: [authGuard],
    children: [
      ...adminRoutes
    ]
  },
  {
    path: 'admin/login',
    loadComponent: () => import('./admin/pages/login.component').then(m => m.LoginComponent)
  },
  {
    path: '**',
    redirectTo: ''
  }
];
