import { Component, ElementRef, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpParams } from '@angular/common/http';
import { ApiService } from '../../shared/services/api.service';
import { FileUrlService, ImageRef } from '../../shared/services/file-url.service';
import { EssentialOil } from '../../shared/models/essential-oil.model';
import { MAX_UPLOAD_SIZE, UPLOAD_SIZE_ERROR_MSG } from '../../shared/constants';

@Component({
  selector: 'app-admin-essential-oils',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-2xl font-bold text-gray-800">精油管理</h2>
        <button (click)="openModal()" class="btn-primary">新增精油</button>
      </div>

      <div class="bg-white rounded-xl shadow-md overflow-hidden">
        <div class="px-6 py-4 border-b border-gray-100 flex flex-col sm:flex-row sm:items-center gap-3">
          <div class="relative w-full sm:w-96">
            <input type="text" name="searchKeyword" autocomplete="off"
              [(ngModel)]="searchKeyword" (ngModelChange)="onSearchInput()"
              placeholder="輸入關鍵字搜尋名稱"
              class="w-full border border-gray-300 rounded-lg pl-4 pr-9 py-2 text-sm focus:ring-2 focus:ring-amber-500 outline-none">
            <button *ngIf="searchKeyword" type="button" (click)="clearSearch()" aria-label="清除關鍵字"
              class="absolute right-2 top-1/2 -translate-y-1/2 leading-none text-gray-400 hover:text-gray-600 transition-colors">×</button>
          </div>
          <span class="text-xs text-gray-500 sm:ml-auto">共 {{ totalElements }} 筆符合結果</span>
        </div>
        <table class="w-full">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">圖片</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">名稱</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">介紹</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">功效</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">注意事項</th>
              <th class="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200">
            <tr *ngFor="let eo of essentialOils">
              <td class="px-6 py-4">
                <div class="w-16 h-16 bg-amber-100 rounded-lg overflow-hidden">
                  <img [src]="fileUrl(eo.images)" class="w-full h-full object-cover" loading="lazy">
                </div>
              </td>
              <td class="px-6 py-4 text-sm text-gray-900">{{ eo.name }}</td>
              <td class="px-6 py-4 text-sm text-gray-500 ">{{ eo.scentNotes }}</td>
              <td class="px-6 py-4 text-sm text-gray-500 ">{{ eo.benefits }}</td>
              <td class="px-6 py-4 text-sm text-gray-500 ">{{ eo.precautions }}</td>
              <td class="px-6 py-4">
                <div class="flex flex-row flex-nowrap items-center justify-end gap-2">
                  <button (click)="editEssentialOil(eo)" class="btn-primary !px-4 !py-1.5 text-sm whitespace-nowrap shrink-0 text-center [writing-mode:horizontal-tb]">編輯</button>
                  <button (click)="deleteEssentialOil(eo.id)" class="btn-primary !px-4 !py-1.5 text-sm whitespace-nowrap shrink-0 text-center [writing-mode:horizontal-tb]">刪除</button>
                </div>
              </td>
            </tr>
            <tr *ngIf="essentialOils.length === 0">
              <td colspan="6" class="px-6 py-10 text-center text-sm text-gray-400">
                {{ searchKeyword.trim() ? '查無符合「' + searchKeyword.trim() + '」的精油' : '尚無精油資料，請點右上角新增' }}
              </td>
            </tr>
          </tbody>
        </table>
        <div class="flex flex-col sm:flex-row items-center justify-between gap-3 px-6 py-4 border-t border-gray-100 bg-gray-50/60">
          <span class="text-sm text-gray-500">共 {{ totalElements }} 筆 · 每頁 {{ pageSize }} 筆 · 第 {{ totalPages ? page + 1 : 1 }} / {{ totalPages || 1 }} 頁</span>
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

      <div *ngIf="showModal" class="fixed inset-0 bg-black bg-opacity-50 flex justify-center z-50 overflow-auto py-8">
        <div class="m-auto bg-white rounded-xl shadow-xl w-full max-w-lg p-6">
          <h3 class="text-xl font-bold text-gray-800 mb-4">{{ isEdit ? '編輯精油' : '新增精油' }}</h3>
          <form (ngSubmit)="saveEssentialOil()" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">名稱</label>
              <input type="text" [(ngModel)]="currentEssentialOil.name" name="name" required class="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-amber-500 outline-none">
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">介紹</label>
              <textarea [(ngModel)]="currentEssentialOil.scentNotes" name="scentNotes" rows="3" class="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-amber-500 outline-none resize-none"></textarea>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">功效</label>
              <textarea [(ngModel)]="currentEssentialOil.benefits" name="benefits" rows="2" class="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-amber-500 outline-none resize-none"></textarea>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">注意事項</label>
              <textarea [(ngModel)]="currentEssentialOil.precautions" name="precautions" rows="2" class="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-amber-500 outline-none resize-none"></textarea>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">圖片上傳</label>
              <div *ngIf="currentEssentialOil.images?.length" class="mb-2">
                <p class="text-xs text-gray-500 mb-1">既有圖片（點 × 移除）</p>
                <div class="flex flex-wrap gap-2">
                  <div *ngFor="let img of currentEssentialOil.images" class="relative w-20 h-20">
                    <img [src]="imageSrc(img.imagePath)" class="w-full h-full object-cover rounded-lg" loading="lazy"
                      [attr.alt]="'圖片 ' + img.id">
                    <button type="button" (click)="deleteImage(img.id)" aria-label="移除這張圖片"
                      class="absolute -top-1.5 -right-1.5 w-5 h-5 rounded-full bg-red-600 text-white text-xs leading-none shadow hover:bg-red-700">×</button>
                  </div>
                </div>
              </div>
              <input type="file" (change)="onFileSelected($event)" accept="image/*" class="w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-semibold file:bg-amber-50 file:text-amber-700 hover:file:bg-amber-100">
              <p *ngIf="fileError" class="mt-2 text-sm text-red-600">{{ fileError }}</p>
              <div *ngIf="selectedImagePreview" class="mt-2">
                <img [src]="selectedImagePreview" class="w-20 h-20 object-cover rounded-lg">
              </div>
            </div>
            <p *ngIf="saveError" class="text-sm text-red-600">{{ saveError }}</p>
            <div class="flex justify-end space-x-2">
              <button type="button" (click)="closeModal()" class="btn-secondary">取消</button>
              <button type="submit" class="btn-primary">儲存</button>
            </div>
          </form>
        </div>
      </div>
    </div>
    <button *ngIf="showScrollTop" (click)="scrollToTop()" aria-label="回到頂端" class="fixed bottom-6 right-6 z-40 w-11 h-11 rounded-full bg-amber-600 text-white shadow-lg hover:bg-amber-700 transition-colors flex items-center justify-center">
      <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M5 15l7-7 7 7" />
      </svg>
    </button>
  `
})
export class EssentialOilsComponent implements OnInit, OnDestroy {
  essentialOils: EssentialOil[] = [];
  page = 0;
  pageSize = 5;
  totalElements = 0;
  totalPages = 0;
  showModal = false;
  isEdit = false;
  currentEssentialOil: Partial<EssentialOil> = {};
  selectedImagePreview: string | null = null;
  selectedFile: File | null = null;
  fileError: string | null = null;
  saveError: string | null = null;
  showScrollTop = false;
  searchKeyword = '';
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private scrollContainer: HTMLElement | null = null;
  private scrollListener = () => this.onScroll();

  constructor(
    private apiService: ApiService,
    private el: ElementRef,
    private fileUrlService: FileUrlService
  ) {}

  /** 圖片 URL 統一出口：無圖時退回本機佔位圖（見 FileUrlService） */
  fileUrl(images?: ReadonlyArray<ImageRef> | null): string {
    return this.fileUrlService.primaryUrl(images);
  }

  /** 單張圖片（圖庫縮圖）的 URL，走同一個 FileUrlService 出口 */
  imageSrc(path?: string | null): string {
    return this.fileUrlService.url(path ?? null);
  }

  /**
   * 移除單張圖片（review M2：補上傳後無法移除個別圖片的缺口）。
   * 後端回傳更新後的整筆 EssentialOil，直接以回傳值更新圖庫，避免多打一次列表請求。
   */
  deleteImage(imageId: number): void {
    if (!this.currentEssentialOil.id || !confirm('確定要移除這張圖片嗎？')) {
      return;
    }
    this.apiService.delete<EssentialOil>(`admin/essential-oils/${this.currentEssentialOil.id}/images/${imageId}`).subscribe({
      next: (updated) => {
        this.currentEssentialOil = { ...this.currentEssentialOil, images: updated.images ?? [] };
        this.loadEssentialOils(this.page);
      },
      error: (err) => {
        this.saveError = ApiService.getErrorMessage(err, '移除圖片失敗，請稍後再試');
      }
    });
  }

  ngOnInit(): void {
    this.loadEssentialOils();
    this.scrollContainer = this.findScrollContainer();
    this.scrollContainer?.addEventListener('scroll', this.scrollListener);
  }

  ngOnDestroy(): void {
    this.scrollContainer?.removeEventListener('scroll', this.scrollListener);
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
  }

  private findScrollContainer(): HTMLElement | null {
    let parent = this.el.nativeElement.parentElement as HTMLElement | null;
    while (parent) {
      const overflowY = getComputedStyle(parent).overflowY;
      if (overflowY === 'auto' || overflowY === 'scroll') {
        return parent;
      }
      parent = parent.parentElement;
    }
    return null;
  }

  onScroll(): void {
    if (this.scrollContainer) {
      this.showScrollTop = this.scrollContainer.scrollTop > 300;
    }
  }

  scrollToTop(): void {
    this.scrollContainer?.scrollTo({ top: 0, behavior: 'smooth' });
  }

  loadEssentialOils(p: number = this.page): void {
    let params = new HttpParams()
      .set('page', String(p))
      .set('size', String(this.pageSize))
      .set('sort', 'id,desc');
    const keyword = this.searchKeyword.trim();
    if (keyword) {
      params = params.set('keyword', keyword);
    }
    this.apiService.getPage<EssentialOil>('admin/essential-oils', params).subscribe({
      next: (pg) => {
        this.essentialOils = pg.content;
        this.page = pg.number;
        this.totalElements = pg.totalElements;
        this.totalPages = pg.totalPages;
      },
      error: (err) => {
        console.error('載入精油失敗', err);
        this.essentialOils = [];
        this.totalElements = 0;
        this.totalPages = 0;
      }
    });
  }

  onSearchInput(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(() => this.loadEssentialOils(0), 300);
  }

  clearSearch(): void {
    this.searchKeyword = '';
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
      this.searchTimer = null;
    }
    this.loadEssentialOils(0);
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

  openModal(): void {
    this.currentEssentialOil = {};
    this.selectedImagePreview = null;
    this.selectedFile = null;
    this.fileError = null;
    this.saveError = null;
    this.isEdit = false;
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
  }

  editEssentialOil(eo: EssentialOil): void {
    this.currentEssentialOil = { ...eo };
    this.selectedImagePreview = null;
    this.selectedFile = null;
    this.fileError = null;
    this.saveError = null;
    this.isEdit = true;
    this.showModal = true;
  }

  saveEssentialOil(): void {
    const onError = (err: any) => {
      this.saveError = ApiService.getErrorMessage(err, '儲存失敗，請稍後再試');
    };
    const onSaved = (id: number | undefined) => {
      this.closeModal();
      this.uploadImage(id);
    };
    if (this.isEdit && this.currentEssentialOil.id) {
      this.apiService.put(`admin/essential-oils/${this.currentEssentialOil.id}`, this.currentEssentialOil).subscribe({
        next: () => onSaved(this.currentEssentialOil.id),
        error: onError
      });
    } else {
      this.apiService.post<EssentialOil>('admin/essential-oils', this.currentEssentialOil).subscribe({
        next: (created) => onSaved(created.id),
        error: onError
      });
    }
  }

  private uploadImage(essentialOilId: number | undefined): void {
    if (essentialOilId && this.selectedFile) {
      this.apiService.upload(`admin/essential-oils/${essentialOilId}/images`, this.selectedFile, {
        sortOrder: '0',
        isPrimary: 'true'
      }).subscribe({
        next: () => this.loadEssentialOils(this.page),
        error: () => this.loadEssentialOils(this.page)
      });
    } else {
      this.loadEssentialOils(this.page);
    }
  }

  deleteEssentialOil(id: number): void {
    if (confirm('確定要刪除此精油嗎？')) {
      this.apiService.delete(`admin/essential-oils/${id}`).subscribe({
        next: () => {
          if (this.essentialOils.length <= 1 && this.page > 0) {
            this.loadEssentialOils(this.page - 1);
          } else {
            this.loadEssentialOils(this.page);
          }
        },
        error: () => {}
      });
    }
  }

  onFileSelected(event: any): void {
    const file = event.target.files[0];
    if (file) {
      if (file.size > MAX_UPLOAD_SIZE) {
        this.fileError = UPLOAD_SIZE_ERROR_MSG;
        this.selectedFile = null;
        this.selectedImagePreview = null;
        event.target.value = '';
        return;
      }
      this.fileError = null;
      this.selectedFile = file;
      const reader = new FileReader();
      reader.onload = (e: any) => {
        this.selectedImagePreview = e.target.result;
      };
      reader.readAsDataURL(file);
    }
  }
}
