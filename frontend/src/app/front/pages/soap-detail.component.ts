import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../shared/services/api.service';
import { FileUrlService, ImageRef } from '../../shared/services/file-url.service';
import { Soap } from '../../shared/models/soap.model';

@Component({
  selector: 'app-soap-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="max-w-7xl mx-auto px-4 py-8" *ngIf="soap">
      <div class="bg-white rounded-xl shadow-md overflow-hidden">
        <div class="grid grid-cols-1 md:grid-cols-2 gap-8 p-6">
          <div class="bg-amber-100 rounded-xl overflow-hidden">
            <img [src]="fileUrl(soap.images)" [alt]="soap.name" class="w-full h-full object-cover aspect-square">
          </div>

          <div>
            <h1 class="text-3xl font-bold text-amber-900 mb-4">{{ soap.name }}</h1>
            <p class="text-gray-600 mb-6 text-lg">{{ soap.shortDescription }}</p>

            <div class="space-y-4 mb-6">
              <div class="flex items-center" *ngIf="soap.category">
                <span class="text-gray-500 w-20">分類:</span>
                <span class="bg-amber-100 text-amber-800 px-3 py-1 rounded-full text-sm">{{ soap.category.name }}</span>
              </div>
            </div>

            <div class="border-t pt-4 mb-6">
              <h3 class="font-semibold text-amber-900 mb-2">成分</h3>
              <p class="text-gray-600" *ngIf="getOilNames()">使用油品：{{ getOilNames() }}</p>
              <p class="text-gray-600" *ngIf="getEssentialOilNames()">使用精油：{{ getEssentialOilNames() }}</p>
              <p class="text-gray-600" *ngIf="soap.ingredients">其他成分：{{ soap.ingredients }}</p>
              <p class="text-gray-400 text-sm" *ngIf="!getOilNames() && !getEssentialOilNames() && !soap.ingredients">尚未提供成分資訊</p>
            </div>

            <div class="border-t pt-4 mb-6">
              <h3 class="font-semibold text-amber-900 mb-2">使用方式</h3>
              <p class="text-gray-600">{{ soap.usageInstructions }}</p>
            </div>

            <div class="flex gap-4">
              <a routerLink="/soaps" class="btn-secondary">返回列表</a>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="max-w-7xl mx-auto px-4 py-8" *ngIf="!soap">
      <div class="text-center py-12">
        <p class="text-gray-500">載入中...</p>
      </div>
    </div>
  `
})
export class SoapDetailComponent implements OnInit {
  soap: Soap | null = null;

  constructor(
    private route: ActivatedRoute,
    private apiService: ApiService,
    private fileUrlService: FileUrlService
  ) {}

  /** 圖片 URL 統一出口：無圖時退回本機佔位圖（見 FileUrlService） */
  fileUrl(images?: ReadonlyArray<ImageRef> | null): string {
    return this.fileUrlService.primaryUrl(images);
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.apiService.get<Soap>(`soaps/${id}`).subscribe({
        next: (data) => this.soap = data,
        error: () => this.soap = this.getMockSoap()
      });
    }
  }

  getOilNames(): string {
    return (this.soap?.oils ?? []).map(o => o.name).join('、');
  }

  getEssentialOilNames(): string {
    return (this.soap?.essentialOils ?? []).map(o => o.name).join('、');
  }

  private getMockSoap(): Soap {
    return {
      id: 1,
      name: '薰衣草舒緩皂',
      shortDescription: '採用法國薰衣草精油與天然植物油製作，泡沫細緻溫和，能幫助放鬆身心、舒緩肌膚疲勞。適合一般膚質使用。',
      fullDescription: '完整描述',
      price: 180,
      weight: '100g',
      dimensions: '',
      usageInstructions: '將皂沾水搓揉起泡後，塗抹於臉部或身體，按摩片刻後用清水沖洗乾淨。',
      storageInstructions: '請置於乾燥通風處，避免積水。',
      ingredients: '氫氧化鈉、純水',
      features: '',
      oils: [
        { id: 1, name: '椰子油' },
        { id: 2, name: '棕櫚油' },
        { id: 3, name: '橄欖油' },
        { id: 4, name: '葵花籽油' }
      ],
      essentialOils: [{ id: 1, name: '薰衣草精油' }],
      isFeatured: true,
      isActive: true,
      category: { id: 1, name: '滋潤型' },
      images: [],
      createdAt: new Date(),
      updatedAt: new Date()
    };
  }
}
