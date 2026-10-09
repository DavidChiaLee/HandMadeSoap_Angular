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

    <div class="max-w-7xl mx-auto px-4 py-8" *ngIf="!soap && isLoading">
      <div class="text-center py-12">
        <p class="text-gray-500">載入中...</p>
      </div>
    </div>

    <div class="max-w-7xl mx-auto px-4 py-8" *ngIf="!soap && !isLoading && errorMessage">
      <div class="bg-red-50 border border-red-200 rounded-xl p-6 text-center">
        <p class="text-red-700 mb-4">{{ errorMessage }}</p>
        <div class="flex gap-4 justify-center">
          <button (click)="retry()" class="btn-primary">重試</button>
          <a routerLink="/soaps" class="btn-secondary">返回列表</a>
        </div>
      </div>
    </div>
  `
})
export class SoapDetailComponent implements OnInit {
  soap: Soap | null = null;
  isLoading = false;
  errorMessage: string | null = null;

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
      this.loadSoap(id);
    }
  }

  loadSoap(id: string): void {
    this.isLoading = true;
    this.errorMessage = null;
    this.soap = null;
    this.apiService.get<Soap>(`soaps/${id}`).subscribe({
      next: (data) => {
        this.isLoading = false;
        this.soap = data;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = ApiService.getErrorMessage(err, '載入商品失敗，請稍後再試');
      }
    });
  }

  retry(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadSoap(id);
    }
  }

  getOilNames(): string {
    return (this.soap?.oils ?? []).map(o => o.name).join('、');
  }

  getEssentialOilNames(): string {
    return (this.soap?.essentialOils ?? []).map(o => o.name).join('、');
  }
}
