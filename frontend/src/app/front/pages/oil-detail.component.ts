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
            <img [src]="fileUrl(oil.images)" [alt]="oil.name" class="w-full h-full object-cover aspect-square">
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

    <div class="max-w-7xl mx-auto px-4 py-8" *ngIf="!oil">
      <div class="text-center py-12">
        <p class="text-gray-500">載入中...</p>
      </div>
    </div>
  `
})
export class OilDetailComponent implements OnInit {
  oil: Oil | null = null;

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
      this.apiService.get<Oil>(`oils/${id}`).subscribe({
        next: (data) => this.oil = data,
        error: () => this.oil = this.getMockOil()
      });
    }
  }

  private getMockOil(): Oil {
    return {
      id: 1,
      name: '橄欖油',
      origin: '被譽為「液體黃金」的橄欖油，富含維生素E、K和抗氧化物',
      benefits: '深層滋潤、抗氧化、維持肌膚彈性、溫和親膚',
      suitableFor: '乾性肌膚、一般肌膚皆適用',
      isActive: true,
      images: [],
      createdAt: new Date(),
      updatedAt: new Date()
    };
  }
}
