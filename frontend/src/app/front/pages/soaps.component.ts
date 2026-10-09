import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpParams } from '@angular/common/http';
import { ApiService } from '../../shared/services/api.service';
import { FileUrlService, ImageRef } from '../../shared/services/file-url.service';
import { Soap } from '../../shared/models/soap.model';

@Component({
  selector: 'app-soaps',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    <div class="max-w-7xl mx-auto px-4 py-8">
      <h1 class="text-3xl font-bold text-amber-900 mb-8">手工皂系列</h1>

      <div class="flex flex-col lg:flex-row gap-8">
        <aside class="lg:w-64 flex-shrink-0">
          <div class="bg-white rounded-xl p-6 shadow-md">
            <h3 class="text-lg font-semibold text-amber-900 mb-4">分類篩選</h3>
            <div *ngIf="categoryError" class="text-sm text-red-600 mb-2">{{ categoryError }} <button (click)="retryCategories()" class="underline">重試</button></div>
            <div class="space-y-2">
              <button (click)="filterByCategory(null)" [class.bg-amber-100]="!selectedCategory" class="w-full text-left px-4 py-2 rounded-lg hover:bg-amber-50 transition-colors">全部</button>
              <button *ngFor="let cat of categories" (click)="filterByCategory(cat.id)" [class.bg-amber-100]="selectedCategory === cat.id" class="w-full text-left px-4 py-2 rounded-lg hover:bg-amber-50 transition-colors">{{ cat.name }}</button>
            </div>

            <h3 class="text-lg font-semibold text-amber-900 mt-6 mb-4">排序</h3>
            <select [(ngModel)]="sortBy" (change)="sortProducts()" class="w-full border border-gray-300 rounded-lg px-4 py-2">
              <option value="name">名稱</option>
              <!-- <option value="price-low">價格低至高</option>
              <option value="price-high">價格高至低</option> -->
            </select>
          </div>
        </aside>

        <div class="flex-1">
          <div *ngIf="isLoading" class="text-center py-12 text-gray-500">載入中...</div>

          <div *ngIf="!isLoading && errorMessage" class="bg-red-50 border border-red-200 rounded-xl p-6 text-center">
            <p class="text-red-700 mb-4">{{ errorMessage }}</p>
            <button (click)="retry()" class="btn-primary">重試</button>
          </div>

          <div *ngIf="!isLoading && !errorMessage && soaps.length === 0" class="text-center py-12 text-gray-500">查無符合的手工皂</div>

          <div *ngIf="!isLoading && !errorMessage && soaps.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            <div *ngFor="let soap of soaps" class="bg-white rounded-xl overflow-hidden shadow-md hover:shadow-xl transition-shadow">
              <div class="h-48 bg-amber-100 flex items-center justify-center">
                <img [src]="fileUrl(soap.images)" [alt]="soap.name" class="w-full h-full object-cover" loading="lazy">
              </div>
              <div class="p-4">
                <h3 class="font-bold text-amber-900 mb-1">{{ soap.name }}</h3>
                <p class="text-gray-600 text-sm mb-2 line-clamp-2">{{ soap.shortDescription }}</p>
                <div class="flex items-center justify-between">
                  <!-- <span class="text-amber-700 font-bold">\${{ soap.price }}</span> -->
                  <a [routerLink]="['/soaps', soap.id]" class="text-sm text-amber-600 hover:text-amber-800">查看詳情</a>
                </div>
              </div>
            </div>
          </div>

          <div *ngIf="totalPages > 0" class="flex justify-center mt-8">
            <div class="flex items-center gap-1.5">
              <button (click)="firstPage()" [disabled]="page === 0" class="min-w-9 px-3 py-1.5 rounded-lg border text-sm transition-colors border-gray-200 text-gray-600 hover:bg-amber-50 hover:border-amber-300 disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:border-gray-200">«</button>
              <button (click)="prevPage()" [disabled]="page === 0" class="min-w-9 px-3 py-1.5 rounded-lg border text-sm transition-colors border-gray-200 text-gray-600 hover:bg-amber-50 hover:border-amber-300 disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:border-gray-200">‹</button>
              <ng-container *ngFor="let n of getPageNumbers()">
                <button *ngIf="n !== '...'" (click)="onPageNumberClick(n)" [class.bg-amber-600]="n === page + 1" [class.text-white]="n === page + 1" [class.border-amber-600]="n === page + 1" [class.font-semibold]="n === page + 1" [class.shadow]="n === page + 1" class="min-w-9 px-3 py-1.5 rounded-lg border text-sm transition-colors border-gray-200 text-gray-600 hover:bg-amber-50 hover:border-amber-300">{{ n }}</button>
                <span *ngIf="n === '...'" class="px-1 text-gray-400 select-none">…</span>
              </ng-container>
              <button (click)="nextPage()" [disabled]="page + 1 >= totalPages" class="min-w-9 px-3 py-1.5 rounded-lg border text-sm transition-colors border-gray-200 text-gray-600 hover:bg-amber-50 hover:border-amber-300 disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:border-gray-200">›</button>
              <button (click)="lastPage()" [disabled]="page + 1 >= totalPages" class="min-w-9 px-3 py-1.5 rounded-lg border text-sm transition-colors border-gray-200 text-gray-600 hover:bg-amber-50 hover:border-amber-300 disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:border-gray-200">»</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class SoapsComponent implements OnInit {
  soaps: Soap[] = [];
  categories: any[] = [];
  selectedCategory: number | null = null;
  sortBy = 'name';
  page = 0;
  pageSize = 8;
  totalPages = 0;
  isLoading = false;
  errorMessage: string | null = null;
  categoryError: string | null = null;

  constructor(
    private apiService: ApiService,
    private route: ActivatedRoute,
    private fileUrlService: FileUrlService
  ) {}

  /** 圖片 URL 統一出口：無圖時退回本機佔位圖（見 FileUrlService） */
  fileUrl(images?: ReadonlyArray<ImageRef> | null): string {
    return this.fileUrlService.primaryUrl(images);
  }

  ngOnInit(): void {
    this.loadCategories();
    this.route.queryParams.subscribe(params => {
      this.selectedCategory = params['category'] ? +params['category'] : null;
      this.loadSoaps(0);
    });
  }

  loadSoaps(p: number = this.page): void {
    this.isLoading = true;
    this.errorMessage = null;
    let params = new HttpParams()
      .set('page', p)
      .set('size', this.pageSize)
      .set('sort', this.getSortParam());
    if (this.selectedCategory != null) {
      params = params.set('categoryId', this.selectedCategory);
    }
    this.apiService.getPage<Soap>('soaps', params).subscribe({
      next: (pg) => {
        this.isLoading = false;
        this.soaps = pg.content;
        this.page = pg.number;
        this.totalPages = pg.totalPages;
      },
      error: (err) => {
        this.isLoading = false;
        this.soaps = [];
        this.totalPages = 0;
        this.errorMessage = ApiService.getErrorMessage(err, '載入手工皂失敗，請稍後再試');
      }
    });
  }

  retry(): void {
    this.loadSoaps(this.page);
  }

  private getSortParam(): string {
    switch (this.sortBy) {
      case 'price-low':
        return 'price,asc';
      case 'price-high':
        return 'price,desc';
      default:
        return 'name,asc';
    }
  }

  loadCategories(): void {
    this.categoryError = null;
    this.apiService.get<any[]>('categories').subscribe({
      next: (data) => this.categories = data,
      error: (err) => {
        this.categories = [];
        this.categoryError = ApiService.getErrorMessage(err, '分類載入失敗，請稍後再試');
      }
    });
  }

  retryCategories(): void {
    this.loadCategories();
  }

  filterByCategory(categoryId: number | null): void {
    this.selectedCategory = categoryId;
    this.loadSoaps(0);
  }

  sortProducts(): void {
    this.loadSoaps(0);
  }

  goToPage(p: number): void {
    if (p >= 0 && p < this.totalPages && p !== this.page) {
      this.loadSoaps(p);
    }
  }

  firstPage(): void {
    this.goToPage(0);
  }

  prevPage(): void {
    this.goToPage(this.page - 1);
  }

  nextPage(): void {
    this.goToPage(this.page + 1);
  }

  lastPage(): void {
    this.goToPage(this.totalPages - 1);
  }

  getPageNumbers(): (number | string)[] {
    const total = this.totalPages;
    if (total <= 0) {
      return [];
    }
    const cur = this.page + 1;
    if (total <= 7) {
      return Array.from({ length: total }, (_, i) => i + 1);
    }
    const pages = new Set<number>([1, 2, cur - 1, cur, cur + 1, total - 1, total]);
    const sorted = [...pages].filter(n => n >= 1 && n <= total).sort((a, b) => a - b);
    const result: (number | string)[] = [];
    let prev = 0;
    for (const n of sorted) {
      if (prev && n - prev > 1) {
        result.push('...');
      }
      result.push(n);
      prev = n;
    }
    return result;
  }

  onPageNumberClick(n: number | string): void {
    if (typeof n === 'number') {
      this.goToPage(n - 1);
    }
  }
}
