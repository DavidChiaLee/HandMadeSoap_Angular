import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpParams } from '@angular/common/http';
import { ApiService } from '../../shared/services/api.service';
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
            <div class="space-y-2">
              <button (click)="filterByCategory(null)" [class.bg-amber-100]="!selectedCategory" class="w-full text-left px-4 py-2 rounded-lg hover:bg-amber-50 transition-colors">全部</button>
              <button *ngFor="let cat of categories" (click)="filterByCategory(cat.id)" [class.bg-amber-100]="selectedCategory === cat.id" class="w-full text-left px-4 py-2 rounded-lg hover:bg-amber-50 transition-colors">{{ cat.name }}</button>
            </div>

            <h3 class="text-lg font-semibold text-amber-900 mt-6 mb-4">排序</h3>
            <select [(ngModel)]="sortBy" (change)="sortProducts()" class="w-full border border-gray-300 rounded-lg px-4 py-2">
              <option value="name">名稱</option>
              <option value="price-low">價格低至高</option>
              <option value="price-high">價格高至低</option>
            </select>
          </div>
        </aside>

        <div class="flex-1">
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            <div *ngFor="let soap of soaps" class="bg-white rounded-xl overflow-hidden shadow-md hover:shadow-xl transition-shadow">
              <div class="h-48 bg-amber-100 flex items-center justify-center">
                <img [src]="soap.images && soap.images.length > 0 ? ('http://localhost:8080/uploads/' + soap.images[0].imagePath) : 'https://via.placeholder.com/300x200'" [alt]="soap.name" class="w-full h-full object-cover">
              </div>
              <div class="p-4">
                <h3 class="font-bold text-amber-900 mb-1">{{ soap.name }}</h3>
                <p class="text-gray-600 text-sm mb-2 line-clamp-2">{{ soap.shortDescription }}</p>
                <div class="flex items-center justify-between">
                  <span class="text-amber-700 font-bold">\${{ soap.price }}</span>
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

  constructor(
    private apiService: ApiService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.loadCategories();
    this.route.queryParams.subscribe(params => {
      this.selectedCategory = params['category'] ? +params['category'] : null;
      this.loadSoaps(0);
    });
  }

  loadSoaps(p: number = this.page): void {
    const endpoint = this.selectedCategory ? `soaps/category/${this.selectedCategory}` : 'soaps';
    const params = new HttpParams()
      .set('page', p)
      .set('size', this.pageSize)
      .set('sort', this.getSortParam());
    this.apiService.getPage<Soap>(endpoint, params).subscribe({
      next: (pg) => {
        this.soaps = pg.content;
        this.page = pg.number;
        this.totalPages = pg.totalPages;
      },
      error: () => this.loadMockSoaps(p)
    });
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
    this.apiService.get<any[]>('categories').subscribe({
      next: (data) => this.categories = data,
      error: () => {
        this.categories = [
          { id: 1, name: '滋潤型' },
          { id: 2, name: '清潔型' },
          { id: 3, name: '敏感型' },
          { id: 4, name: '嬰兒型' }
        ];
      }
    });
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

  private loadMockSoaps(p: number): void {
    let filtered = this.getMockSoaps();

    if (this.selectedCategory) {
      filtered = filtered.filter(s => s.category && s.category.id === this.selectedCategory);
    }

    switch (this.sortBy) {
      case 'price-low':
        filtered.sort((a, b) => (a.price || 0) - (b.price || 0));
        break;
      case 'price-high':
        filtered.sort((a, b) => (b.price || 0) - (a.price || 0));
        break;
      default:
        filtered.sort((a, b) => a.name.localeCompare(b.name));
    }

    this.totalPages = Math.ceil(filtered.length / this.pageSize);
    this.page = Math.min(p, Math.max(0, this.totalPages - 1));
    const start = this.page * this.pageSize;
    this.soaps = filtered.slice(start, start + this.pageSize);
  }

  private getMockSoaps(): Soap[] {
    return [
      { id: 1, name: '薰衣草舒緩皂', shortDescription: '添加薰衣草精油，幫助放鬆身心', fullDescription: '', price: 180, weight: '100g', dimensions: '', usageInstructions: '', storageInstructions: '', ingredients: '', features: '', isFeatured: true, isActive: true, category: { id: 1, name: '滋潤型' }, images: [], createdAt: new Date(), updatedAt: new Date() },
      { id: 2, name: '茶樹淨膚皂', shortDescription: '深層清潔，適合油性肌膚', fullDescription: '', price: 150, weight: '100g', dimensions: '', usageInstructions: '', storageInstructions: '', ingredients: '', features: '', isFeatured: true, isActive: true, category: { id: 2, name: '清潔型' }, images: [], createdAt: new Date(), updatedAt: new Date() },
      { id: 3, name: '玫瑰保濕皂', shortDescription: '溫和保濕，散發玫瑰香氣', fullDescription: '', price: 200, weight: '100g', dimensions: '', usageInstructions: '', storageInstructions: '', ingredients: '', features: '', isFeatured: true, isActive: true, category: { id: 1, name: '滋潤型' }, images: [], createdAt: new Date(), updatedAt: new Date() },
      { id: 4, name: '檸檬亮膚皂', shortDescription: '提亮膚色，清新檸檬香', fullDescription: '', price: 160, weight: '100g', dimensions: '', usageInstructions: '', storageInstructions: '', ingredients: '', features: '', isFeatured: false, isActive: true, category: { id: 2, name: '清潔型' }, images: [], createdAt: new Date(), updatedAt: new Date() },
      { id: 5, name: '燕麥溫和皂', shortDescription: '極度溫和，適合敏感性肌膚', fullDescription: '', price: 170, weight: '100g', dimensions: '', usageInstructions: '', storageInstructions: '', ingredients: '', features: '', isFeatured: false, isActive: true, category: { id: 3, name: '敏感型' }, images: [], createdAt: new Date(), updatedAt: new Date() },
      { id: 6, name: '嬰兒洗顏皂', shortDescription: '專為嬰兒設計，無刺激', fullDescription: '', price: 220, weight: '80g', dimensions: '', usageInstructions: '', storageInstructions: '', ingredients: '', features: '', isFeatured: false, isActive: true, category: { id: 4, name: '嬰兒型' }, images: [], createdAt: new Date(), updatedAt: new Date() }
    ];
  }
}
