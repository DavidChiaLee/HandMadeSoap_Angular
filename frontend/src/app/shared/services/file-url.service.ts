import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { PLACEHOLDER_IMAGE } from '../constants';

/**
 * 後端各 entity 的 images 陣列元素都帶有 imagePath。
 * 這裡只依賴實際會用到的欄位，避免 service 綁死單一 model。
 */
export interface ImageRef {
  imagePath?: string | null;
}

/** 補上結尾斜線，避免 uploadUrl 漏寫斜線時拼出 /uploadsxxx.png */
export function toBaseUrl(uploadUrl: string): string {
  return uploadUrl.endsWith('/') ? uploadUrl : `${uploadUrl}/`;
}

/**
 * 圖片 URL 的唯一出口。
 *
 * 正式環境的上傳目錄由 nginx 反向代理（`environment.uploadUrl = '/uploads/'`），
 * 因此圖片一律與網站同源；本機開發則指向 `http://localhost:8080/uploads/`。
 * 模板一律使用 `[src]="fileUrl(images)"`，不得再自行拼接 host/port。
 */
@Injectable({
  providedIn: 'root'
})
export class FileUrlService {
  private readonly baseUrl: string;

  constructor() {
    this.baseUrl = toBaseUrl(environment.uploadUrl);
  }

  /**
   * 將後端回傳的圖片路徑轉成可直接使用的 URL。
   * 空值退回本機佔位圖；已是絕對 URL 或 data URI 者原樣沿用。
   */
  url(path: string | null | undefined): string {
    if (!path || typeof path !== 'string') {
      return PLACEHOLDER_IMAGE;
    }
    // 後端若改回傳完整網址（或外部圖床、data URI），不再二次拼接
    if (/^(https?:)?\/\//i.test(path) || path.startsWith('data:')) {
      return path;
    }
    return `${this.baseUrl}${path.replace(/^\/+/, '')}`;
  }

  /** 取 images 陣列第一張圖的 URL；沒有圖片時退回佔位圖 */
  primaryUrl(images?: ReadonlyArray<ImageRef> | null): string {
    const first = images && images.length > 0 ? images[0] : null;
    return this.url(first?.imagePath);
  }
}
