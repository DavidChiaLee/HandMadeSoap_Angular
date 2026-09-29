import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../shared/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, CommonModule],
  template: `
    <div class="min-h-screen flex items-center justify-center bg-amber-50">
      <div class="max-w-md w-full bg-white rounded-xl shadow-md p-8">
        <h2 class="text-2xl font-bold text-center text-amber-900 mb-6">後台登入</h2>

        <form (ngSubmit)="onSubmit()" class="space-y-4">
          <div>
            <label for="username" class="block text-sm font-medium text-gray-700 mb-1">帳號</label>
            <input type="text" id="username" name="username" [(ngModel)]="credentials.username" required class="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-amber-500 focus:border-amber-500 outline-none">
          </div>

          <div>
            <label for="password" class="block text-sm font-medium text-gray-700 mb-1">密碼</label>
            <input type="password" id="password" name="password" [(ngModel)]="credentials.password" required class="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-amber-500 focus:border-amber-500 outline-none">
          </div>

          <div *ngIf="errorMessage" class="bg-red-50 border border-red-200 rounded-lg p-3 text-red-700">
            {{ errorMessage }}
          </div>

          <button type="submit" class="w-full btn-primary">登入</button>
        </form>
      </div>
    </div>
  `
})
export class LoginComponent {
  credentials = {
    username: '',
    password: ''
  };

  errorMessage = '';

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  onSubmit(): void {
    this.errorMessage = '';

    this.authService.login(this.credentials).subscribe({
      next: (response) => {
        this.router.navigate(['/admin/dashboard']);
      },
      error: (err) => {
        this.errorMessage = err.error?.message || '登入失敗，請檢查您的帳號密碼';
      }
    });
  }
}
