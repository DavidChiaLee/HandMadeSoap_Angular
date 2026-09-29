import { Component } from '@angular/core';

@Component({
  selector: 'app-about',
  standalone: true,
  imports: [],
  template: `
    <div class="max-w-7xl mx-auto px-4 py-8">
      <h1 class="section-title">關於我們</h1>

      <div class="bg-white rounded-xl shadow-md p-8 mb-8">
        <h2 class="text-2xl font-bold text-amber-900 mb-4">品牌故事</h2>
        <p class="text-gray-600 leading-relaxed mb-4">
          手工皂坊創立於2020年，始於對天然、健康生活的熱愛。我們相信，回歸自然的呵護方式，是最好的護膚選擇。
        </p>
        <p class="text-gray-600 leading-relaxed">
          創辦人因家人肌膚敏感問題，開始研究天然手工皂製作。透過不斷嘗試與學習，我們找到了最適合肌膚的配方，並決定將這份天然的美好分享給更多人。
        </p>
      </div>

      <div class="bg-white rounded-xl shadow-md p-8 mb-8">
        <h2 class="text-2xl font-bold text-amber-900 mb-4">製作過程</h2>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div class="text-center">
            <div class="w-16 h-16 bg-amber-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <span class="text-2xl font-bold text-amber-700">1</span>
            </div>
            <h3 class="font-semibold text-amber-900 mb-2">精選原料</h3>
            <p class="text-gray-600 text-sm">嚴選天然植物油與精油，確保品質純淨</p>
          </div>
          <div class="text-center">
            <div class="w-16 h-16 bg-amber-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <span class="text-2xl font-bold text-amber-700">2</span>
            </div>
            <h3 class="font-semibold text-amber-900 mb-2">低溫皂化</h3>
            <p class="text-gray-600 text-sm">採用冷製法，保留油脂營養成分</p>
          </div>
          <div class="text-center">
            <div class="w-16 h-16 bg-amber-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <span class="text-2xl font-bold text-amber-700">3</span>
            </div>
            <h3 class="font-semibold text-amber-900 mb-2">熟成靜置</h3>
            <p class="text-gray-600 text-sm">自然熟成4-6週，確保皂體溫和不刺激</p>
          </div>
        </div>
      </div>

      <div class="bg-amber-50 rounded-xl p-8">
        <h2 class="text-2xl font-bold text-amber-900 mb-4">我們的承諾</h2>
        <ul class="space-y-3 text-gray-600">
          <li class="flex items-start">
            <svg class="w-6 h-6 text-amber-600 mr-2 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
            </svg>
            <span>100% 天然成分，無添加化學防腐劑</span>
          </li>
          <li class="flex items-start">
            <svg class="w-6 h-6 text-amber-600 mr-2 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
            </svg>
            <span>環保包裝，減少塑膠使用</span>
          </li>
          <li class="flex items-start">
            <svg class="w-6 h-6 text-amber-600 mr-2 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
            </svg>
            <span>手工製作，每塊皂都是獨一無二</span>
          </li>
        </ul>
      </div>
    </div>
  `
})
export class AboutComponent {}
