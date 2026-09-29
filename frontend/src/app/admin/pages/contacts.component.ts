import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpParams } from '@angular/common/http';
import { ApiService } from '../../shared/services/api.service';
import { Contact } from '../../shared/models/contact.model';

@Component({
  selector: 'app-admin-contacts',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-2xl font-bold text-gray-800">聯絡訊息管理</h2>
      </div>

      <div *ngIf="actionError" class="mb-4 p-4 bg-red-50 text-red-700 rounded-lg">
        {{ actionError }}
      </div>

      <div class="bg-white rounded-xl shadow-md overflow-hidden">
        <table class="w-full">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">姓名</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Email</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">主旨</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">日期</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">狀態</th>
              <th class="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200">
            <tr *ngFor="let contact of contacts">
              <td class="px-6 py-4 text-sm text-gray-900">{{ contact.name }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ contact.email }}</td>
              <td class="px-6 py-4 text-sm text-gray-900">{{ contact.subject }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ contact.createdAt | date:'yyyy-MM-dd' }}</td>
              <td class="px-6 py-4">
                <span [class.bg-green-100]="contact.isReplied" [class.text-green-800]="contact.isReplied" [class.bg-yellow-100]="!contact.isReplied" [class.text-yellow-800]="!contact.isReplied" class="px-2 py-1 rounded-full text-xs font-medium">
                  {{ contact.isReplied ? '已回覆' : '未回覆' }}
                </span>
              </td>
              <td class="px-6 py-4 text-right space-x-2">
                <button (click)="viewContact(contact)" class="text-amber-600 hover:text-amber-800">查看</button>
                <button (click)="toggleReplied(contact)" class="text-blue-600 hover:text-blue-800">
                  {{ contact.isReplied ? '標記未回覆' : '標記已回覆' }}
                </button>
                <button (click)="deleteContact(contact.id)" class="text-red-600 hover:text-red-800">刪除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div *ngIf="showModal" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 overflow-auto py-8">
        <div class="bg-white rounded-xl shadow-xl w-full max-w-2xl p-6">
          <h3 class="text-xl font-bold text-gray-800 mb-4">訊息詳情</h3>
          <div class="space-y-3">
            <div class="flex">
              <span class="text-gray-500 w-20">姓名:</span>
              <span class="text-gray-900">{{ selectedContact?.name }}</span>
            </div>
            <div class="flex">
              <span class="text-gray-500 w-20">Email:</span>
              <span class="text-gray-900">{{ selectedContact?.email }}</span>
            </div>
            <div class="flex">
              <span class="text-gray-500 w-20">電話:</span>
              <span class="text-gray-900">{{ selectedContact?.phone || '-' }}</span>
            </div>
            <div class="flex">
              <span class="text-gray-500 w-20">主旨:</span>
              <span class="text-gray-900">{{ selectedContact?.subject }}</span>
            </div>
            <div>
              <span class="text-gray-500">訊息:</span>
              <p class="text-gray-900 mt-1 bg-gray-50 p-3 rounded-lg">{{ selectedContact?.message }}</p>
            </div>
          </div>
          <div class="flex justify-end mt-6">
            <button (click)="closeModal()" class="btn-secondary">關閉</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class ContactsComponent implements OnInit {
  contacts: Contact[] = [];
  showModal = false;
  selectedContact: Contact | null = null;
  actionError: string | null = null;

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.loadContacts();
  }

  loadContacts(): void {
    this.apiService.get<Contact[]>('admin/contacts').subscribe({
      next: (data) => this.contacts = data,
      error: (err) => {
        console.error('載入聯絡訊息失敗', err);
        this.contacts = [];
      }
    });
  }

  viewContact(contact: Contact): void {
    this.selectedContact = contact;
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
  }

  /**
   * 切換回覆狀態。後端提供 PUT /{id}/replied-status 只接收 isReplied，
   * 不送整筆 Contact，避免與後端欄位耦合。
   */
  toggleReplied(contact: Contact): void {
    const params = new HttpParams().set('isReplied', String(!contact.isReplied));
    this.apiService.put(`admin/contacts/${contact.id}/replied-status`, null, params).subscribe({
      next: () => this.loadContacts(),
      error: (err) => {
        console.error('切換回覆狀態失敗', err);
        this.actionError = ApiService.getErrorMessage(err, '更新回覆狀態失敗，請稍後再試');
      }
    });
  }

  deleteContact(id: number): void {
    if (confirm('確定要刪除此訊息嗎？')) {
      this.apiService.delete(`admin/contacts/${id}`).subscribe({
        next: () => this.loadContacts(),
        error: (err) => {
          console.error('刪除聯絡訊息失敗', err);
          this.actionError = ApiService.getErrorMessage(err, '刪除失敗，請稍後再試');
        }
      });
    }
  }
}
