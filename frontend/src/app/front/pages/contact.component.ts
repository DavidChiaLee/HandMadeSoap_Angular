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
        <form (ngSubmit)="onSubmit()" class="space-y-6 relative">
          <!-- honeypot 蜜罐欄位（review 728）：真人看不到（off-screen），
               bot 自動填表時會填滿它，後端發現非空即判定為 bot（silent success）。
               刻意不用 display:none —— 部分 bot 會跳過完全不可見的欄位；
               tabindex=-1 避免鍵盤 Tab 誤入，aria-hidden 讓螢幕閱讀器略過。 -->
          <div aria-hidden="true" class="absolute top-0 -left-[9999px] h-px w-px overflow-hidden">
            <label for="company">公司名稱</label>
            <input type="text" id="company" name="company" [(ngModel)]="contactForm.company"
                   tabindex="-1" autocomplete="off">
          </div>
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

          <button type="submit" class="w-full btn-primary" [disabled]="isSubmitting">
            {{ isSubmitting ? '送出中...' : '送出訊息' }}
          </button>
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
    message: '',
    // honeypot：正常情況恆為空字串（見模板註解），後端對非空值 silent success。
    company: ''
  };

  submitted = false;
  submitError = false;
  errorMessage = '';
  /** 連點防護：送出期間鎖住按鈕與 onSubmit。 */
  isSubmitting = false;

  /**
   * 表單建立（或上次成功送出）的時間 —— 最短填表時間判斷用。
   * 只是前端擋「秒填機器人」的第一道粗糙防線；後端不信任此值
   * （honeypot / 限流 / 去重才是真正的防線）。
   */
  formOpenedAt = Date.now();

  /** 少於此毫秒數就按送出視為機器行為（真人逐欄填寫幾乎不可能這麼快）。 */
  private static readonly MIN_FILL_MS = 2000;

  constructor(private apiService: ApiService) {}

  onSubmit(): void {
    if (this.isSubmitting) {
      return;
    }
    this.submitted = false;
    this.submitError = false;
    this.errorMessage = '';

    if (Date.now() - this.formOpenedAt < ContactComponent.MIN_FILL_MS) {
      this.submitError = true;
      this.errorMessage = '送出過快，請確認內容後再試';
      return;
    }

    this.isSubmitting = true;
    this.apiService.post('contacts', this.contactForm).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.submitted = true;
        this.contactForm = { name: '', email: '', phone: '', subject: '', message: '', company: '' };
        // 重新計時：讓使用者有正常的填寫時間再送出下一則
        this.formOpenedAt = Date.now();
      },
      error: (err) => {
        // 送出失敗時不可顯示成功訊息，否則使用者會誤以為已收到回覆
        this.isSubmitting = false;
        this.submitError = true;

        // 429 = 後端 ContactSubmissionThrottleService 或 nginx limit_req 觸發。
        // 必須與一般錯誤分開：此時使用者該「等待」而非重填表單；
        // 後端附有 Retry-After（秒），優先採用後端訊息。
        if (err?.status === 429) {
          this.errorMessage =
            err?.error?.message ||
            (err?.headers?.get('Retry-After')
              ? `送出過於頻繁，請於 ${err.headers.get('Retry-After')} 秒後再試`
              : '送出過於頻繁，請稍後再試');
          return;
        }
        // 409 = 內容重複（連點／重複貼上）；其餘沿用後端訊息，拿不到時退回通用提示。
        this.errorMessage = ApiService.getErrorMessage(err, '送出失敗，請稍後再試');
      }
    });
  }
}
