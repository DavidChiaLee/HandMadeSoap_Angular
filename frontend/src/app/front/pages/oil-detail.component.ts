import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../shared/services/api.service';
import { FileUrlService, ImageRef } from '../../shared/services/file-url.service';
import { Oil } from '../../shared/models/oil.model';

@Component({
  selector: 'app-oil-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="max-w-7xl mx-auto px-4 py-8" *ngIf="oil">
      <div class="bg-white rounded-xl shadow-md overflow-hidden">
        <div class="grid grid-cols-1 md:grid-cols-2 gap-8 p-6">
          <div class="bg-amber-100 rounded-xl overflow-hidden">
            <img [src]="fileUrl(oil.images)" [alt]="oil.name" class="w-full h-full object-cover aspect-square" width="600" height="600">
          </div>

          <div>
            <h1 class="text-3xl font-bold text-amber-900 mb-4">{{ oil.name }}</h1>
            <p class="text-gray-600 mb-6 text-lg">{{ oil.origin }}</p>

            <div class="border-t pt-4 mb-6">
              <h3 class="font-semibold text-amber-900 mb-2">功效</h3>
              <p class="text-gray-600">{{ oil.benefits }}</p>
            </div>

            <div class="border-t pt-4 mb-6">
              <h3 class="font-semibold text-amber-900 mb-2">適用對象</h3>
              <p class="text-gray-600">{{ oil.suitableFor }}</p>
            </div>

            <a routerLink="/oils" class="btn-secondary">返回列表</a>
          </div>
        </div>
      </div>
    </div>

    <div class="max-w-7xl mx-auto px-4 py-8" *ngIf="!oil && isLoading">
      <div class="text-center py-12">
        <p class="text-gray-500">載入中...</p>
      </div>
    </div>

    <div class="max-w-7xl mx-auto px-4 py-8" *ngIf="!oil && !isLoading && errorMessage">
      <div class="bg-red-50 border border-red-200 rounded-xl p-6 text-center">
        <p class="text-red-700 mb-4">{{ errorMessage }}</p>
        <div class="flex gap-4 justify-center">
          <button (click)="retry()" class="btn-primary">重試</button>
          <a routerLink="/oils" class="btn-secondary">返回列表</a>
        </div>
      </div>
    </div>
  `
})
export class OilDetailComponent implements OnInit {
  oil: Oil | null = null;
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
      this.loadOil(id);
    }
  }

  loadOil(id: string): void {
    this.isLoading = true;
    this.errorMessage = null;
    this.oil = null;
    this.apiService.get<Oil>(`oils/${id}`).subscribe({
      next: (data) => {
        this.isLoading = false;
        this.oil = data;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = ApiService.getErrorMessage(err, '載入油品失敗，請稍後再試');
      }
    });
  }

  retry(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadOil(id);
    }
  }
}
