import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, map, startWith } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

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
            @if (isAdminRoute()) {
              @if (isLoggedIn()) {
                <span class="text-sm text-gray-700">{{ currentUsername()?.username || '已登入' }}</span>
              } @else {
                <a routerLink="/admin/login" class="text-sm text-gray-500 hover:text-amber-700 transition-colors">後台登入</a>
              }
            }
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
          @if (isAdminRoute()) {
            @if (isLoggedIn()) {
              <span class="block px-4 py-2 text-sm text-gray-700">{{ currentUsername()?.username || '已登入' }}</span>
            } @else {
              <a routerLink="/admin/login" (click)="toggleMobileMenu()" class="block px-4 py-2 text-sm text-gray-500 hover:bg-amber-50 rounded">後台登入</a>
            }
          }
        </div>
      </div>
    </nav>
  `
})
export class NavbarComponent {
  private router = inject(Router);
  private authService = inject(AuthService);

  /**
   * 目前是否位於後台路由（/admin 與 /admin/login 底下）。
   *
   * 為什麼需要：前台是給一般訪客看的，不該出現「後台登入」入口。
   * app-navbar 掛在 app.component 的根層，後台頁面也會渲染它，
   * 因此必須依當前路由決定是否顯示。
   *
   * 為什麼用 toSignal 而非直接讀 router.url：
   * router.url 不是響應式來源，使用者點擊後不會觸發變更偵測。
   * 轉成 signal 後模板才能在每次導航後自動重算。
   *
   * startWith 不可省略：使用者直接開啟 /admin/login 或重新整理後台頁面時，
   * 尚未收到第一個 NavigationEnd，沒有它 navbar 會先顯示錯誤內容再跳動。
   */
  readonly isAdminRoute = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map(() => this.router.url.startsWith('/admin')),
      startWith(this.router.url.startsWith('/admin'))
    ),
    { initialValue: false }
  );

  /** 是否已登入；token 過期被 auth.interceptor 登出時會自動轉為 false */
  readonly isLoggedIn = toSignal(this.authService.isAuthenticated$, { initialValue: false });

  /** 目前登入者；用於顯示使用者名稱（欄位是 username，後端沒有 name） */
  readonly currentUsername = toSignal(this.authService.currentUser$, { initialValue: null });

  isMobileMenuOpen = false;

  toggleMobileMenu(): void {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }
}
