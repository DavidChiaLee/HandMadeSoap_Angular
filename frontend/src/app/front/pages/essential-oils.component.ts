import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpParams } from '@angular/common/http';
import { ApiService } from '../../shared/services/api.service';
import { FileUrlService, ImageRef } from '../../shared/services/file-url.service';
import { EssentialOil } from '../../shared/models/essential-oil.model';

@Component({
  selector: 'app-essential-oils',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="max-w-7xl mx-auto px-4 py-8">
      <h1 class="text-3xl font-bold text-amber-900 mb-8">天然精油</h1>

      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        <div *ngFor="let eo of essentialOils" class="bg-white rounded-xl overflow-hidden shadow-md hover:shadow-xl transition-shadow">
          <div class="h-48 bg-amber-100 flex items-center justify-center">
            <img [src]="fileUrl(eo.images)" [alt]="eo.name" class="w-full h-full object-cover">
          </div>
          <div class="p-6">
            <h3 class="text-xl font-bold text-amber-900 mb-2">{{ eo.name }}</h3>
            <p class="text-gray-600 mb-4 line-clamp-3">{{ eo.scentNotes }}</p>
            <a [routerLink]="['/essential-oils', eo.id]" class="text-amber-700 hover:text-amber-900 font-medium">了解更多 →</a>
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
export class EssentialOilsComponent implements OnInit {
  essentialOils: EssentialOil[] = [];
  page = 0;
  pageSize = 8;
  totalPages = 0;

  constructor(
    private apiService: ApiService,
    private fileUrlService: FileUrlService
  ) {}

  /** 圖片 URL 統一出口：無圖時退回本機佔位圖（見 FileUrlService） */
  fileUrl(images?: ReadonlyArray<ImageRef> | null): string {
    return this.fileUrlService.primaryUrl(images);
  }

  ngOnInit(): void {
    this.loadEssentialOils();
  }

  loadEssentialOils(p: number = this.page): void {
    const params = new HttpParams()
      .set('page', p)
      .set('size', this.pageSize)
      .set('sort', 'id,desc');
    this.apiService.getPage<EssentialOil>('essential-oils', params).subscribe({
      next: (pg) => {
        this.essentialOils = pg.content;
        this.page = pg.number;
        this.totalPages = pg.totalPages;
      },
      error: () => this.loadMockEssentialOils(p)
    });
  }

  goToPage(p: number): void {
    if (p >= 0 && p < this.totalPages && p !== this.page) {
      this.loadEssentialOils(p);
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

  private loadMockEssentialOils(p: number): void {
    const all = this.getMockEssentialOils();
    this.totalPages = Math.ceil(all.length / this.pageSize);
    this.page = Math.min(p, Math.max(0, this.totalPages - 1));
    const start = this.page * this.pageSize;
    this.essentialOils = all.slice(start, start + this.pageSize);
  }

  private getMockEssentialOils(): EssentialOil[] {
    return [
      { id: 1, name: '薰衣草精油', scentNotes: '舒緩放鬆', benefits: '幫助入睡、撫平情緒', precautions: '孕婦避免使用', isActive: true, images: [], createdAt: new Date(), updatedAt: new Date() },
      { id: 2, name: '茶樹精油', scentNotes: '淨化抗菌', benefits: '清潔肌膚、抗菌', precautions: '需稀釋後使用', isActive: true, images: [], createdAt: new Date(), updatedAt: new Date() },
      { id: 3, name: '檸檬精油', scentNotes: '清新提神', benefits: '提亮膚色、振奮精神', precautions: '光敏性，使用後避光', isActive: true, images: [], createdAt: new Date(), updatedAt: new Date() },
      { id: 4, name: '玫瑰精油', scentNotes: '優雅花香', benefits: '滋潤保濕、舒緩情緒', precautions: '敏感肌膚先測試', isActive: true, images: [], createdAt: new Date(), updatedAt: new Date() }
    ];
  }
}
