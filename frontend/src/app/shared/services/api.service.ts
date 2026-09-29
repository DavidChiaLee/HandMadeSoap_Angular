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

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private baseUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  private getUrl(endpoint: string): string {
    return `${this.baseUrl}/${endpoint}`;
  }

  get<T>(endpoint: string, params?: HttpParams): Observable<T> {
    return this.http.get<{ success: boolean; data: any }>(this.getUrl(endpoint), { params }).pipe(
      map(res => this.unwrap(res.data) as T)
    );
  }

  getPage<T>(endpoint: string, params?: HttpParams): Observable<Page<T>> {
    return this.http.get<{ success: boolean; data: Page<T> }>(this.getUrl(endpoint), { params }).pipe(
      map(res => res.data)
    );
  }

  post<T>(endpoint: string, body: any): Observable<T> {
    return this.http.post<{ success: boolean; data: any }>(this.getUrl(endpoint), body).pipe(
      map(res => this.unwrap(res.data) as T)
    );
  }

  put<T>(endpoint: string, body: any, params?: HttpParams): Observable<T> {
    return this.http
      .put<{ success: boolean; data: any }>(this.getUrl(endpoint), body, { params })
      .pipe(map(res => this.unwrap(res.data) as T));
  }

  delete<T>(endpoint: string): Observable<T> {
    return this.http.delete<{ success: boolean; data: any }>(this.getUrl(endpoint)).pipe(
      map(res => this.unwrap(res.data) as T)
    );
  }

  upload<T>(endpoint: string, file: File, extraParams?: Record<string, string>): Observable<T> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    if (extraParams) {
      Object.keys(extraParams).forEach((key) => {
        formData.append(key, extraParams[key]);
      });
    }
    return this.http.post<{ success: boolean; data: any }>(this.getUrl(endpoint), formData).pipe(
      map(res => this.unwrap(res.data) as T)
    );
  }

  /**
   * 後端列表 API 統一以 Spring 的 Page<T> 包裝資料（真正的陣列在 data.content 中）。
   * 若回傳的是帶有 content 陣列的物件，就解包成內容陣列；否則原樣回傳，避免各頁面誤用。
   */
  private unwrap(data: any): any {
    if (data && typeof data === 'object' && !Array.isArray(data) && Array.isArray(data.content)) {
      return data.content;
    }
    return data;
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
