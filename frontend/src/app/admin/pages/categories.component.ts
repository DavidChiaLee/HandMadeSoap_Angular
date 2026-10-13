import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../shared/services/api.service';
import { Category } from '../../shared/models/category.model';

@Component({
  selector: 'app-categories',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-2xl font-bold text-gray-800">分類管理</h2>
        <button (click)="openModal()" class="btn-primary">新增分類</button>
      </div>

      <div *ngIf="loadError" class="mb-4 p-4 bg-red-50 text-red-700 rounded-lg">
        資料載入失敗，請確認後端服務正常後重新整理頁面。
      </div>

      <div class="bg-white rounded-xl shadow-md overflow-hidden">
        <table class="w-full">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">ID</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">名稱</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">描述</th>
              <th class="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200">
            <tr *ngFor="let category of categories">
              <td class="px-6 py-4 text-sm text-gray-900">{{ category.id }}</td>
              <td class="px-6 py-4 text-sm text-gray-900">{{ category.name }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ category.description }}</td>
              <td class="px-6 py-4 text-right space-x-2">
                <button (click)="editCategory(category)" class="text-amber-600 hover:text-amber-800">編輯</button>
                <button (click)="deleteCategory(category.id)" class="text-red-600 hover:text-red-800">刪除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div *ngIf="showModal" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
        <div class="bg-white rounded-xl shadow-xl w-full max-w-md p-6">
          <h3 class="text-xl font-bold text-gray-800 mb-4">{{ isEdit ? '編輯分類' : '新增分類' }}</h3>
          <form (ngSubmit)="saveCategory()" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">名稱</label>
              <input type="text" [(ngModel)]="currentCategory.name" name="name" required class="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-amber-500 outline-none">
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">描述</label>
              <textarea [(ngModel)]="currentCategory.description" name="description" rows="3" class="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-amber-500 outline-none resize-none"></textarea>
            </div>
            <div class="flex justify-end space-x-2">
              <button type="button" (click)="closeModal()" class="btn-secondary">取消</button>
              <button type="submit" class="btn-primary">儲存</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `
})
export class CategoriesComponent implements OnInit {
  categories: Category[] = [];
  showModal = false;
  isEdit = false;
  loadError = false;
  currentCategory: Partial<Category> = {};

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.loadCategories();
  }

  loadCategories(): void {
    this.loadError = false;
    this.apiService.getList<Category>('admin/categories').subscribe({
      next: (data) => this.categories = data,
      error: (err) => {
        console.error('載入分類失敗', err);
        this.loadError = true;
        this.categories = [];
      }
    });
  }

  openModal(): void {
    this.currentCategory = {};
    this.isEdit = false;
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
  }

  editCategory(category: Category): void {
    this.currentCategory = { ...category };
    this.isEdit = true;
    this.showModal = true;
  }

  saveCategory(): void {
    const action$ = this.isEdit && this.currentCategory.id
      ? this.apiService.put(`admin/categories/${this.currentCategory.id}`, this.currentCategory)
      : this.apiService.post('admin/categories', this.currentCategory);

    action$.subscribe({
      next: () => {
        this.closeModal();
        this.loadCategories();
      },
      error: (err) => {
        // 401 由 authInterceptor 處理登出並導向登入頁，不需在此彈出錯誤
        if (err?.status === 401) { return; }
        const msg = err?.error?.message || err?.message || '請稍後再試';
        alert(`${this.isEdit ? '更新' : '新增'}分類失敗：${msg}`);
        console.error('儲存分類失敗', err);
      }
    });
  }

  deleteCategory(id: number): void {
    if (confirm('確定要刪除此分類嗎？')) {
      this.apiService.delete(`admin/categories/${id}`).subscribe({
        next: () => this.loadCategories(),
        error: (err) => {
          if (err?.status === 401) { return; }
          const msg = err?.error?.message || err?.message || '請稍後再試';
          alert(`刪除分類失敗：${msg}`);
          console.error('刪除分類失敗', err);
        }
      });
    }
  }
}
