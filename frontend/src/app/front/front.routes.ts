import { Routes } from '@angular/router';

export const frontRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/home.component').then(m => m.HomeComponent)
  },
  {
    path: 'soaps',
    loadComponent: () => import('./pages/soaps.component').then(m => m.SoapsComponent)
  },
  {
    path: 'soaps/:id',
    loadComponent: () => import('./pages/soap-detail.component').then(m => m.SoapDetailComponent)
  },
  {
    path: 'oils',
    loadComponent: () => import('./pages/oils.component').then(m => m.OilsComponent)
  },
  {
    path: 'oils/:id',
    loadComponent: () => import('./pages/oil-detail.component').then(m => m.OilDetailComponent)
  },
  {
    path: 'essential-oils',
    loadComponent: () => import('./pages/essential-oils.component').then(m => m.EssentialOilsComponent)
  },
  {
    path: 'essential-oils/:id',
    loadComponent: () => import('./pages/essential-oil-detail.component').then(m => m.EssentialOilDetailComponent)
  },
  {
    path: 'about',
    loadComponent: () => import('./pages/about.component').then(m => m.AboutComponent)
  },
  {
    path: 'contact',
    loadComponent: () => import('./pages/contact.component').then(m => m.ContactComponent)
  }
];
