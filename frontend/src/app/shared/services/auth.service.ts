import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApiService } from './api.service';
import { LoginRequest, LoginResponse } from '../models/login.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  // 只存在記憶體（非 localStorage）→ 避免 XSS 竊取；15 分鐘內由
  // /auth/refresh 自動續拿。登入頁重新整理也不會清掉 access token。
  private accessToken: string | null = null;
  private userKey = 'current_user';
  private isAuthenticatedSubject = new BehaviorSubject<boolean>(this.hasToken());

  /**
   * 目前登入者的資料流。
   *
   * 為什麼需要這個：{@link getCurrentUser()} 是「同步讀 localStorage」，
   * 不會隨登入／登出變化而通知訂閱者。若 navbar 在建構時呼叫它並存成欄位，
   * 登入後畫面不會重算，必須重新整理才會顯示使用者名稱。
   *
   * 因此在 setUser() / logout() 中主動 next()，讓 UI 能即時反應
   * （例如 token 過期被 auth.interceptor 登出時，navbar 要自動回到登入連結）。
   */
  private currentUserSubject = new BehaviorSubject<LoginResponse | null>(this.getCurrentUser());

  isAuthenticated$ = this.isAuthenticatedSubject.asObservable();
  currentUser$ = this.currentUserSubject.asObservable();

  constructor(
    private apiService: ApiService,
    private router: Router
  ) {}

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.apiService.post<LoginResponse>('auth/login', credentials).pipe(
      map(response => {
        if (response.accessToken) {
          // accessToken 只放記憶體，不落 localStorage（防 XSS）
          this.accessToken = response.accessToken;
          this.isAuthenticatedSubject.next(true);
          // 只存可顯示欄位；refreshToken 在 HttpOnly Cookie，交給瀏覽器與後端處理。
          this.setUser({
            username: response.username,
            role: response.role
          });
        }
        return response;
      })
    );
  }

  logout(): void {
    // 先通知後端撤銷（review M8 Phase 1+2）：bump admin.token_version 並作廢
    // refresh DB 欄位 + 清 refresh Cookie。必須在清除記憶體 token「之前」發出 ——
    // auth.interceptor 是從 getToken() 取 Bearer header 的，先清就送不出去了。
    // best-effort：後端失敗（離線/token 已失效）仍要完成本機登出，
    // 且錯誤刻意吞掉 —— 登出是終點，沒有值得回報的失敗。
    const hadToken = this.getToken() !== null;
    if (hadToken) {
      // logout 仍帶 Bearer（靠記憶體 token），interceptor 會自動加 header；
      // Cookie（refresh_token）由瀏覽器自動帶出（interceptor 統一加 withCredentials）。
      this.apiService.post('auth/logout', {}).subscribe({ error: () => {} });
    }
    this.accessToken = null;
    localStorage.removeItem(this.userKey);
    this.isAuthenticatedSubject.next(false);
    this.currentUserSubject.next(null);
    this.router.navigate(['/admin/login']);
  }

  /**
   * 用 HttpOnly Cookie 的 refresh_token 換新 access token（M8 Phase 2）。
   * 由 auth.interceptor 在 401 時呼叫；元件一般不直接呼叫。
   * 成功時把新 access token 寫回記憶體並回傳；失敗時直接拋錯給 interceptor
   * 觸發 logout（refresh 已失效 = 必須重新登入）。
   */
  refresh(): Observable<LoginResponse> {
    // refresh_token 由瀏覽器經 Cookie 自動帶出（interceptor 統一加 withCredentials），
    // 前端不讀不送 refresh token 本體。
    return this.apiService.post<LoginResponse>('auth/refresh', {}).pipe(
      map(response => {
        if (response.accessToken) {
          this.accessToken = response.accessToken;
          this.isAuthenticatedSubject.next(true);
        }
        return response;
      })
    );
  }

  setToken(token: string): void {
    // 記憶體存 access token；不再寫 localStorage（防 XSS）。
    this.accessToken = token;
    this.isAuthenticatedSubject.next(true);
  }

  getToken(): string | null {
    // 從記憶體取 access token；非 localStorage。
    return this.accessToken;
  }

  setUser(user: any): void {
    localStorage.setItem(this.userKey, JSON.stringify(user));
    this.currentUserSubject.next(user);
  }

  getCurrentUser(): any {
    const user = localStorage.getItem(this.userKey);
    return user ? JSON.parse(user) : null;
  }

  isAuthenticated(): boolean {
    return this.isAuthenticatedSubject.value;
  }

  private hasToken(): boolean {
    return this.accessToken !== null;
  }
}
