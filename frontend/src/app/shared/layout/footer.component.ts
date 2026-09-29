import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [RouterLink],
  template: `
    <footer class="bg-amber-900 text-white py-8">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 md:grid-cols-3 gap-8">
          <div>
            <h3 class="text-xl font-bold mb-4">手工皂坊</h3>
          </div>
          <div>
            <h4 class="font-semibold mb-4">快速連結</h4>
            <ul class="space-y-2">
              <li><a routerLink="/" class="text-amber-200 hover:text-white transition-colors">首頁</a></li>
              <li><a routerLink="/soaps" class="text-amber-200 hover:text-white transition-colors">手工皂</a></li>
              <li><a routerLink="/oils" class="text-amber-200 hover:text-white transition-colors">油品</a></li>
              <li><a routerLink="/about" class="text-amber-200 hover:text-white transition-colors">關於我們</a></li>
              <li><a routerLink="/contact" class="text-amber-200 hover:text-white transition-colors">聯絡我們</a></li>
            </ul>
          </div>
          <div>
            <h4 class="font-semibold mb-4">聯絡資訊</h4>
            <p class="text-amber-200">Email: info&#64;handsoap.com</p>
            <p class="text-amber-200">Phone: +886-123-456-789</p>
          </div>
        </div>
        <div class="border-t border-amber-700 mt-8 pt-6 text-center text-amber-300">
          <p>&copy; 2026 手工皂坊. All rights reserved.</p>
        </div>
      </div>
    </footer>
  `
})
export class FooterComponent {}
