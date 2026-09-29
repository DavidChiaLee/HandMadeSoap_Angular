import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <nav class="bg-white shadow-md sticky top-0 z-50">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <a routerLink="/" class="flex items-center space-x-2">
            <span class="text-2xl font-bold text-amber-800">手工皂坊</span>
          </a>

          <div class="hidden md:flex items-center space-x-8">
            <a routerLink="/" routerLinkActive="text-amber-700" [routerLinkActiveOptions]="{exact: true}" class="text-gray-700 hover:text-amber-700 transition-colors">首頁</a>
            <a routerLink="/soaps" routerLinkActive="text-amber-700" class="text-gray-700 hover:text-amber-700 transition-colors">手工皂</a>
            <a routerLink="/oils" routerLinkActive="text-amber-700" class="text-gray-700 hover:text-amber-700 transition-colors">油品</a>
            <a routerLink="/essential-oils" routerLinkActive="text-amber-700" class="text-gray-700 hover:text-amber-700 transition-colors">精油</a>
            <a routerLink="/about" routerLinkActive="text-amber-700" class="text-gray-700 hover:text-amber-700 transition-colors">關於我們</a>
            <a routerLink="/contact" routerLinkActive="text-amber-700" class="text-gray-700 hover:text-amber-700 transition-colors">聯絡我們</a>
            <a routerLink="/admin/login" class="text-sm text-gray-500 hover:text-amber-700 transition-colors">後台登入</a>
          </div>

          <button (click)="toggleMobileMenu()" class="md:hidden p-2 rounded-md text-gray-700 hover:text-amber-700">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path *ngIf="!isMobileMenuOpen" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16"/>
              <path *ngIf="isMobileMenuOpen" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
            </svg>
          </button>
        </div>

        <div *ngIf="isMobileMenuOpen" class="md:hidden pb-4 space-y-2">
          <a routerLink="/" (click)="toggleMobileMenu()" class="block px-4 py-2 text-gray-700 hover:bg-amber-50 rounded">首頁</a>
          <a routerLink="/soaps" (click)="toggleMobileMenu()" class="block px-4 py-2 text-gray-700 hover:bg-amber-50 rounded">手工皂</a>
          <a routerLink="/oils" (click)="toggleMobileMenu()" class="block px-4 py-2 text-gray-700 hover:bg-amber-50 rounded">油品</a>
          <a routerLink="/essential-oils" (click)="toggleMobileMenu()" class="block px-4 py-2 text-gray-700 hover:bg-amber-50 rounded">精油</a>
          <a routerLink="/about" (click)="toggleMobileMenu()" class="block px-4 py-2 text-gray-700 hover:bg-amber-50 rounded">關於我們</a>
          <a routerLink="/contact" (click)="toggleMobileMenu()" class="block px-4 py-2 text-gray-700 hover:bg-amber-50 rounded">聯絡我們</a>
          <a routerLink="/admin/login" (click)="toggleMobileMenu()" class="block px-4 py-2 text-sm text-gray-500 hover:bg-amber-50 rounded">後台登入</a>
        </div>
      </div>
    </nav>
  `
})
export class NavbarComponent {
  isMobileMenuOpen = false;

  toggleMobileMenu(): void {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }
}
