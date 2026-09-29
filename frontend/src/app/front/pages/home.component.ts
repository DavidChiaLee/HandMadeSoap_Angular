import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpParams } from '@angular/common/http';
import { ApiService } from '../../shared/services/api.service';
import { Category } from '../../shared/models/category.model';
import { Soap } from '../../shared/models/soap.model';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, CommonModule],
  template: `
    <div>
      <section class="relative h-96 md:h-[500px] bg-gradient-to-r from-amber-700 to-amber-900">
        <div class="absolute inset-0 bg-black bg-opacity-30"></div>
        <div class="relative max-w-7xl mx-auto px-4 h-full flex items-center justify-center text-center">
          <div>
            <h1 class="text-4xl md:text-6xl font-bold text-white mb-4">純天然手工皂</h1>
            <p class="text-xl text-amber-100 mb-8">採用天然植物精油與植物油，手工製作每一塊皂</p>
            <a routerLink="/soaps" class="inline-block bg-white text-amber-800 px-8 py-3 rounded-lg font-semibold hover:bg-amber-50 transition-colors">探索產品</a>
          </div>
        </div>
      </section>

      <section class="py-16 bg-white">
        <div class="max-w-7xl mx-auto px-4">
          <h2 class="section-title">精選商品</h2>
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            <div *ngFor="let soap of featuredSoaps" class="bg-amber-50 rounded-xl overflow-hidden shadow-md hover:shadow-xl transition-shadow">
              <div class="h-48 bg-amber-200 flex items-center justify-center">
                <img [src]="soap.images && soap.images.length > 0 ? ('http://localhost:8080/uploads/' + soap.images[0].imagePath) : 'https://via.placeholder.com/300x200'" [alt]="soap.name" class="w-full h-full object-cover">
              </div>
              <div class="p-6">
                <h3 class="text-xl font-bold text-amber-900 mb-2">{{ soap.name }}</h3>
                <p class="text-gray-600 mb-4 line-clamp-2">{{ soap.shortDescription }}</p>
                <a [routerLink]="['/soaps', soap.id]" class="btn-primary">查看詳情</a>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="py-16 bg-amber-50">
        <div class="max-w-7xl mx-auto px-4">
          <h2 class="text-3xl font-bold text-center mb-8 text-amber-900">商品分類</h2>
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            <a *ngFor="let category of categories" [routerLink]="['/soaps']" [queryParams]="{ category: category.id }" class="bg-white rounded-xl p-6 text-center shadow-md hover:shadow-xl transition-shadow">
              <div class="w-16 h-16 bg-amber-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <svg class="w-8 h-8 text-amber-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"/>
                </svg>
              </div>
              <h3 class="text-lg font-semibold text-amber-900">{{ category.name }}</h3>
              <p class="text-gray-500 text-sm mt-1">{{ category.description }}</p>
            </a>
          </div>
        </div>
      </section>
    </div>
  `
})
export class HomeComponent implements OnInit {
  featuredSoaps: Soap[] = [];

  categories: Category[] = [];

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.loadFeaturedSoaps();
    this.loadCategories();
  }

  private loadFeaturedSoaps(): void {
    const params = new HttpParams().set('page', 0).set('size', 6);
    this.apiService.getPage<Soap>('soaps/featured', params).subscribe({
      next: (pg) => {
        if (pg.content.length > 0) {
          this.featuredSoaps = pg.content;
        } else {
          // 後台尚未勾選任何精選商品 → 退回最新上架，避免首頁整塊空白
          this.loadLatestSoaps();
        }
      },
      error: () => this.loadLatestSoaps()
    });
  }

  private loadLatestSoaps(): void {
    const params = new HttpParams()
      .set('page', 0)
      .set('size', 6)
      .set('sort', 'id,desc');
    this.apiService.getPage<Soap>('soaps', params).subscribe({
      next: (pg) => (this.featuredSoaps = pg.content),
      error: () => {
        // 後端無法連線時，退回展示用假資料，避免頁面空白（與 loadCategories 慣例一致）
        this.featuredSoaps = this.getFallbackSoaps();
      }
    });
  }

  private getFallbackSoaps(): Soap[] {
    return [
      { id: 1, name: '薰衣草舒緩皂', shortDescription: '添加薰衣草精油，幫助放鬆身心', fullDescription: '', price: 180, weight: '100g', dimensions: '', usageInstructions: '', storageInstructions: '', ingredients: '', features: '', isFeatured: true, isActive: true, category: { id: 1, name: '滋潤型' }, images: [], createdAt: new Date(), updatedAt: new Date() },
      { id: 2, name: '茶樹淨膚皂', shortDescription: '深層清潔，適合油性肌膚', fullDescription: '', price: 150, weight: '100g', dimensions: '', usageInstructions: '', storageInstructions: '', ingredients: '', features: '', isFeatured: true, isActive: true, category: { id: 2, name: '清潔型' }, images: [], createdAt: new Date(), updatedAt: new Date() },
      { id: 3, name: '玫瑰保濕皂', shortDescription: '溫和保濕，散發玫瑰香氣', fullDescription: '', price: 200, weight: '100g', dimensions: '', usageInstructions: '', storageInstructions: '', ingredients: '', features: '', isFeatured: true, isActive: true, category: { id: 1, name: '滋潤型' }, images: [], createdAt: new Date(), updatedAt: new Date() }
    ];
  }

  private loadCategories(): void {
    this.apiService.get<Category[]>('categories').subscribe({
      next: (data) => {
        // 只顯示後台已啟用的分類，並依後台設定的 sortOrder 排序
        this.categories = data
          .filter(c => c.isActive !== false)
          .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
      },
      error: () => {
        // 後端無法連線時，退回與資料庫 seed 一致的分類，避免頁面空白
        this.categories = [
          { id: 1, name: '一般家事皂', description: '', sortOrder: 1, isActive: true, createdAt: new Date(), updatedAt: new Date() },
          { id: 2, name: '功能性家事皂', description: '', sortOrder: 2, isActive: true, createdAt: new Date(), updatedAt: new Date() },
          { id: 3, name: '可愛造型手工皂', description: '', sortOrder: 3, isActive: true, createdAt: new Date(), updatedAt: new Date() },
          { id: 4, name: '其他手工皂', description: '', sortOrder: 4, isActive: true, createdAt: new Date(), updatedAt: new Date() },
          { id: 5, name: '其他', description: '', sortOrder: 5, isActive: true, createdAt: new Date(), updatedAt: new Date() }
        ];
      }
    });
  }
}
