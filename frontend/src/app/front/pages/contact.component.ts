import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../shared/services/api.service';

@Component({
  selector: 'app-contact',
  standalone: true,
  imports: [FormsModule, CommonModule],
  template: `
    <div class="max-w-7xl mx-auto px-4 py-8">
      <h1 class="section-title">聯絡我們</h1>

      <div class="max-w-2xl mx-auto bg-white rounded-xl shadow-md p-8">
        <form (ngSubmit)="onSubmit()" class="space-y-6">
          <div>
            <label for="name" class="block text-sm font-medium text-gray-700 mb-1">姓名</label>
            <input type="text" id="name" name="name" [(ngModel)]="contactForm.name" required class="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-amber-500 focus:border-amber-500 outline-none">
          </div>

          <div>
            <label for="email" class="block text-sm font-medium text-gray-700 mb-1">電子郵件</label>
            <input type="email" id="email" name="email" [(ngModel)]="contactForm.email" required class="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-amber-500 focus:border-amber-500 outline-none">
          </div>

          <div>
            <label for="phone" class="block text-sm font-medium text-gray-700 mb-1">電話</label>
            <input type="tel" id="phone" name="phone" [(ngModel)]="contactForm.phone" class="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-amber-500 focus:border-amber-500 outline-none">
          </div>

          <div>
            <label for="subject" class="block text-sm font-medium text-gray-700 mb-1">主旨</label>
            <input type="text" id="subject" name="subject" [(ngModel)]="contactForm.subject" required class="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-amber-500 focus:border-amber-500 outline-none">
          </div>

          <div>
            <label for="message" class="block text-sm font-medium text-gray-700 mb-1">訊息內容</label>
            <textarea id="message" name="message" [(ngModel)]="contactForm.message" required rows="5" class="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-amber-500 focus:border-amber-500 outline-none resize-none"></textarea>
          </div>

          <button type="submit" class="w-full btn-primary">送出訊息</button>
        </form>

        <div *ngIf="submitted" class="mt-6 bg-green-50 border border-green-200 rounded-lg p-4 text-green-800">
          感謝您的訊息！我們會盡快回覆您。
        </div>

        <div *ngIf="submitError" class="mt-6 bg-red-50 border border-red-200 rounded-lg p-4 text-red-700">
          {{ errorMessage }}
        </div>
      </div>
    </div>
  `
})
export class ContactComponent {
  contactForm = {
    name: '',
    email: '',
    phone: '',
    subject: '',
    message: ''
  };

  submitted = false;
  submitError = false;
  errorMessage = '';

  constructor(private apiService: ApiService) {}

  onSubmit(): void {
    this.submitted = false;
    this.submitError = false;
    this.errorMessage = '';

    this.apiService.post('contacts', this.contactForm).subscribe({
      next: () => {
        this.submitted = true;
        this.contactForm = { name: '', email: '', phone: '', subject: '', message: '' };
      },
      error: (err) => {
        // 送出失敗時不可顯示成功訊息，否則使用者會誤以為已收到回覆
        this.submitError = true;
        this.errorMessage = ApiService.getErrorMessage(err, '送出失敗，請稍後再試');
      }
    });
  }
}
