import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 後端統一回應包裝（對應 ApiResponse.java），取代各方法散落的 { success; data: any }。 */
interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

/**
 * 執行期契約驗證：判斷 data 是否為 Spring Page<T> 結構。
 * 以型別守衛取代舊版 unwrap() 的結構猜測——呼叫端先驗證再取用，
 * 契約不符時當場 throw 走 error 分支，而非默默錯解。
 */
export function isPage<T>(data: unknown): data is Page<T> {
  return (
    typeof data === 'object' &&
    data !== null &&
    !Array.isArray(data) &&
    Array.isArray((data as Page<T>).content) &&
    typeof (data as Page<T>).totalElements === 'number' &&
    typeof (data as Page<T>).totalPages === 'number' &&
    typeof (data as Page<T>).number === 'number' &&
    typeof (data as Page<T>).size === 'number'
  );
}

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private baseUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  private getUrl(endpoint: string): string {
    return `${this.baseUrl}/${endpoint}`;
  }

  /**
   * 契約：端點回傳非 Page 的單筆資料（或陣列原樣）。
   * 若後端改回傳 Page，代表契約變更，當場 throw 提示改用 getPage/getList。
   */
  get<T>(endpoint: string, params?: HttpParams): Observable<T> {
    return this.http.get<ApiResponse<T>>(this.getUrl(endpoint), { params }).pipe(
      map(res => {
        if (isPage<T>(res.data)) {
          throw new Error(
            `端點 ${endpoint} 回傳 Page 結構，請改用 getPage<T>()（完整分頁）或 getList<T>()（只要內容陣列）`
          );
        }
        return res.data;
      })
    );
  }

  /**
   * 契約：端點回傳 Spring Page<T>，但呼叫端只需要內容陣列
   * （例如分類／油品選單）。以 isPage 驗證後取 content。
   */
  getList<T>(endpoint: string, params?: HttpParams): Observable<T[]> {
    return this.http.get<ApiResponse<unknown>>(this.getUrl(endpoint), { params }).pipe(
      map(res => {
        if (!isPage<T>(res.data)) {
          throw new Error(`端點 ${endpoint} 未回傳 Page 結構，getList<T> 無法取用 content`);
        }
        return res.data.content;
      })
    );
  }

  /**
   * 契約：端點回傳 Spring Page<T>，呼叫端需要完整分頁中繼資料。
   * 以 isPage 驗證後原樣回傳。
   */
  getPage<T>(endpoint: string, params?: HttpParams): Observable<Page<T>> {
    return this.http.get<ApiResponse<unknown>>(this.getUrl(endpoint), { params }).pipe(
      map(res => {
        if (!isPage<T>(res.data)) {
          throw new Error(`端點 ${endpoint} 未回傳 Page 結構，getPage<T> 無法取用分頁資料`);
        }
        return res.data;
      })
    );
  }

  /** 契約：回傳建立／更新後的單筆資料。 */
  post<T>(endpoint: string, body: unknown): Observable<T> {
    return this.http.post<ApiResponse<T>>(this.getUrl(endpoint), body).pipe(
      map(res => res.data)
    );
  }

  /** 契約：回傳更新後的單筆資料。 */
  put<T>(endpoint: string, body: unknown, params?: HttpParams): Observable<T> {
    return this.http
      .put<ApiResponse<T>>(this.getUrl(endpoint), body, { params })
      .pipe(map(res => res.data));
  }

  /** 契約：回傳單筆資料或 null（DELETE 無內容）。 */
  delete<T>(endpoint: string): Observable<T> {
    return this.http.delete<ApiResponse<T>>(this.getUrl(endpoint)).pipe(
      map(res => res.data)
    );
  }

  /** 契約：回傳上傳完成後的單筆資料。 */
  upload<T>(endpoint: string, file: File, extraParams?: Record<string, string>): Observable<T> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    if (extraParams) {
      Object.keys(extraParams).forEach((key) => {
        formData.append(key, extraParams[key]);
      });
    }
    return this.http.post<ApiResponse<T>>(this.getUrl(endpoint), formData).pipe(
      map(res => res.data)
    );
  }

  /**
   * 取得可顯示給使用者的錯誤訊息。
   * 後端 400 驗證錯誤的 data 為 { 欄位: 訊息 } 物件，優先合併顯示；否則取 message。
   */
  static getErrorMessage(err: any, fallback = '操作失敗，請稍後再試'): string {
    const body = err?.error;
    if (body?.data && typeof body.data === 'object' && !Array.isArray(body.data)) {
      const fieldMessages = Object.values(body.data).filter((v): v is string => typeof v === 'string');
      if (fieldMessages.length > 0) {
        return fieldMessages.join('；');
      }
    }
    return body?.message || fallback;
  }
}
