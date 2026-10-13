import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../shared/services/api.service';

/** 後端 /admin/stats 回傳的統計欄位（對應 StatsResponse.java）。 */
interface Stats {
  soaps: number;
  oils: number;
  essentialOils: number;
  contacts: number;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div>
      <h2 class="text-2xl font-bold text-gray-800 mb-6">儀表板</h2>

      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div class="bg-white rounded-xl shadow-md p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-gray-500 text-sm">手工皂</p>
              <p class="text-3xl font-bold text-amber-700">{{ stats.soaps }}</p>
            </div>
            <div class="w-12 h-12 bg-amber-100 rounded-full flex items-center justify-center">
              <svg class="w-6 h-6 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"/>
              </svg>
            </div>
          </div>
          <a routerLink="/admin/soaps" class="text-sm text-amber-600 hover:text-amber-800 mt-4 inline-block">查看詳情 →</a>
        </div>

        <div class="bg-white rounded-xl shadow-md p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-gray-500 text-sm">油品</p>
              <p class="text-3xl font-bold text-amber-700">{{ stats.oils }}</p>
            </div>
            <div class="w-12 h-12 bg-amber-100 rounded-full flex items-center justify-center">
              <svg class="w-6 h-6 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z"/>
              </svg>
            </div>
          </div>
          <a routerLink="/admin/oils" class="text-sm text-amber-600 hover:text-amber-800 mt-4 inline-block">查看詳情 →</a>
        </div>

        <div class="bg-white rounded-xl shadow-md p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-gray-500 text-sm">精油</p>
              <p class="text-3xl font-bold text-amber-700">{{ stats.essentialOils }}</p>
            </div>
            <div class="w-12 h-12 bg-amber-100 rounded-full flex items-center justify-center">
              <svg class="w-6 h-6 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z"/>
              </svg>
            </div>
          </div>
          <a routerLink="/admin/essential-oils" class="text-sm text-amber-600 hover:text-amber-800 mt-4 inline-block">查看詳情 →</a>
        </div>

        <div class="bg-white rounded-xl shadow-md p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-gray-500 text-sm">聯絡訊息</p>
              <p class="text-3xl font-bold text-amber-700">{{ stats.contacts }}</p>
            </div>
            <div class="w-12 h-12 bg-amber-100 rounded-full flex items-center justify-center">
              <svg class="w-6 h-6 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"/>
              </svg>
            </div>
          </div>
          <a routerLink="/admin/contacts" class="text-sm text-amber-600 hover:text-amber-800 mt-4 inline-block">查看詳情 →</a>
        </div>
      </div>
    </div>
  `
})
export class DashboardComponent implements OnInit {
  stats: Stats = {
    soaps: 0,
    oils: 0,
    essentialOils: 0,
    contacts: 0
  };

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.loadStats();
  }

  loadStats(): void {
    this.apiService.get<Stats>('admin/stats').subscribe({
      next: (data) => {
        this.stats = data;
      },
      error: (err) => {
        console.error('載入統計失敗', err);
        this.stats = { soaps: 0, oils: 0, essentialOils: 0, contacts: 0 };
      }
    });
  }
}
