import { Routes } from '@angular/router';
import { AdminLayoutComponent } from './admin-layout.component';

export const adminRoutes: Routes = [
  {
    path: '',
    component: AdminLayoutComponent,
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./pages/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'categories',
        loadComponent: () => import('./pages/categories.component').then(m => m.CategoriesComponent)
      },
      {
        path: 'soaps',
        loadComponent: () => import('./pages/soaps.component').then(m => m.SoapsComponent)
      },
      {
        path: 'oils',
        loadComponent: () => import('./pages/oils.component').then(m => m.OilsComponent)
      },
      {
        path: 'essential-oils',
        loadComponent: () => import('./pages/essential-oils.component').then(m => m.EssentialOilsComponent)
      },
      {
        path: 'contacts',
        loadComponent: () => import('./pages/contacts.component').then(m => m.ContactsComponent)
      },
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      }
    ]
  }
];
