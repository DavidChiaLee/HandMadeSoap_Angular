import { Component, Input, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpParams } from '@angular/common/http';
import { ApiService } from '../../shared/services/api.service';
import { FileUrlService, ImageRef } from '../../shared/services/file-url.service';

export interface OilListConfig {
  title: string;
  placeholder: string;
  emptyWithKeyword: string;
  emptyWithoutKeyword: string;
  detailField: 'scentNotes' | 'benefits';
  routePrefix: string;
  baseEndpoint: string;
}

export interface OilListItem {
  id: number;
  name: string;
  images: ImageRef[];
  [key: string]: any;
}

@Component({
  selector: 'app-oil-search-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    <div class="max-w-7xl mx-auto px-4 py-8">
      <h1 class="text-3xl font-bold text-amber-900 mb-8">{{ config.title }}</h1>

      <!-- 前台搜尋視窗（按鈕觸發，與後台不同） -->
      <div class="bg-white rounded-xl border border-gray-200 shadow-sm p-5 mb-6">
        <div class="flex flex-col sm:flex-row gap-3">
          <div class="relative flex-1">
            <input
              type="text"
              maxlength="100"
              [(ngModel)]="searchKeyword"
              (keydown.enter)="doSearch()"
              placeholder="{{ config.placeholder }}"
              class="w-full border border-gray-300 rounded-lg pl-4 pr-9 py-2.5 text-sm focus:ring-2 focus:ring-amber-500 outline-none"
            />
            <button
              *ngIf="searchKeyword"
              type="button"
              (click)="clearSearch()"
              aria-label="清除關鍵字"
              class="absolute right-2.5 top-1/2 -translate-y-1/2 leading-none text-gray-400 hover:text-gray-600 transition-colors"
            >
              ×
            </button>
          </div>
          <button (click)="doSearch()" class="btn-primary flex-shrink-0">
            搜尋
          </button>
        </div>
        <p *ngIf="searchKeyword && searchResultCount > 0" class="text-sm text-gray-500 mt-2 text-right">
          共 {{ searchResultCount }} 筆符合結果
        </p>
      </div>

      <div *ngIf="isLoading" class="text-center py-12 text-gray-500">{{ isSearching ? '搜尋中...' : '載入中...' }}</div>

      <div *ngIf="!isLoading && errorMessage" class="bg-red-50 border border-red-200 rounded-xl p-6 text-center mb-6">
        <p class="text-red-700 mb-4">{{ errorMessage }}</p>
        <button (click)="retry()" class="btn-primary">重試</button>
      </div>

      <div *ngIf="!isLoading && !errorMessage && items.length === 0" class="text-center py-12 text-gray-500">
        {{ searchKeyword ? config.emptyWithKeyword : config.emptyWithoutKeyword }}
      </div>

      <div *ngIf="!isLoading && !errorMessage && items.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        <div *ngFor="let item of items" class="bg-white rounded-xl overflow-hidden shadow-md hover:shadow-xl transition-shadow">
          <div class="h-48 bg-amber-100 flex items-center justify-center">
            <img [src]="fileUrl(item.images)" [alt]="item.name" class="w-full h-full object-cover" loading="lazy">
          </div>
          <div class="p-6">
            <h3 class="text-xl font-bold text-amber-900 mb-2">{{ item.name }}</h3>
            <p class="text-gray-600 mb-4 line-clamp-3">{{ getDetailField(item) }}</p>
            <a [routerLink]="[config.routePrefix, item.id]" class="text-amber-700 hover:text-amber-900 font-medium">了解更多 →</a>
          </div>
        </div>
      </div>

      <div *ngIf="totalPages > 0" class="flex justify-center mt-10">
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
  `

})
export class OilSearchListComponent implements OnInit {
  @Input() config!: OilListConfig;
  @Input() pageSize = 8;

  items: OilListItem[] = [];
  page = 0;
  totalPages = 0;
  isLoading = false;
  isSearching = false;
  errorMessage: string | null = null;
  searchKeyword: string = '';
  searchResultCount = 0;

  constructor(
    private apiService: ApiService,
    private fileUrlService: FileUrlService
  ) {}

  /** 圖片 URL 統一出口：無圖時退回本機佔位圖（見 FileUrlService） */
  fileUrl(images?: ReadonlyArray<ImageRef> | null): string {
    return this.fileUrlService.primaryUrl(images);
  }

  getDetailField(item: OilListItem): string {
    return item[this.config.detailField] ?? '';
  }

  ngOnInit(): void {
    this.loadItems();
  }

  loadItems(p: number = this.page, keyword?: string): void {
    this.isLoading = true;
    this.errorMessage = null;
    let params = new HttpParams()
      .set('page', p)
      .set('size', this.pageSize)
      .set('sort', 'id,desc');
    if (keyword) {
      params = params.set('keyword', keyword);
    }
    const endpoint = keyword ? `${this.config.baseEndpoint}/search` : this.config.baseEndpoint;
    this.apiService.getPage<OilListItem>(endpoint, params).subscribe({
      next: (pg) => {
        this.isLoading = false;
        this.isSearching = false;
        this.items = pg.content;
        this.page = pg.number;
        this.totalPages = pg.totalPages;
        this.searchResultCount = pg.totalElements;
      },
      error: (err) => {
        this.isLoading = false;
        this.isSearching = false;
        this.items = [];
        this.totalPages = 0;
        this.searchResultCount = 0;
        this.errorMessage = ApiService.getErrorMessage(
          err,
          keyword ? '搜尋失敗，請稍後再試' : `載入${this.config.title}失敗，請稍後再試`
        );
      }
    });
  }

  doSearch(): void {
    this.isSearching = true;
    this.errorMessage = null;
    const keyword = this.searchKeyword.trim();
    this.loadItems(0, keyword || undefined);  // 關鍵字為空時顯示全部
  }

  clearSearch(): void {
    this.searchKeyword = '';
    this.searchResultCount = 0;
    this.isSearching = false;
    this.loadItems(0);
  }

  retry(): void {
    this.loadItems(this.page);
  }

  goToPage(p: number): void {
    if (p >= 0 && p < this.totalPages && p !== this.page) {
      this.loadItems(p);
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