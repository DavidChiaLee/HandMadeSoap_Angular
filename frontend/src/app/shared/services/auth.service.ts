import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApiService } from './api.service';
import { CurrentUser, LoginRequest, LoginResponse } from '../models/login.model';

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
  private currentUserSubject = new BehaviorSubject<CurrentUser | null>(this.getCurrentUser());

  /** 登入頁路徑：guard / interceptor 導向時集中帶 returnUrl，並避免在登入頁重複導向。 */
  private readonly loginUrl = '/admin/login';

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
          // 只存可顯示欄位 + exp（供 guard 判過期）；refreshToken 在 HttpOnly Cookie，
          // 交給瀏覽器與後端處理。
          this.setUser({
            username: response.username,
            role: response.role,
            exp: this.decodeExp(response.accessToken)
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
    this.redirectToLogin();
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
          // 續杯後 exp 也換新，一併回寫，讓 guard 用的是最新過期時間。
          this.setUser({
            username: response.username,
            role: response.role,
            exp: this.decodeExp(response.accessToken)
          });
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

  setUser(user: CurrentUser): void {
    localStorage.setItem(this.userKey, JSON.stringify(user));
    this.currentUserSubject.next(user);
  }

  getCurrentUser(): CurrentUser | null {
    const user = localStorage.getItem(this.userKey);
    return user ? JSON.parse(user) : null;
  }

  isAuthenticated(): boolean {
    return this.isAuthenticatedSubject.value;
  }

  /**
   * token 是否仍有效（供 authGuard 判斷）：
   * 記憶體有 token、current_user 有 exp 且尚未過期。
   *
   * <p>exp 缺漏（舊資料／解碼失敗）時從寬視為有效 —— 真正的把關仍在後端
   * JwtAuthFilter；這裡只為避免「過期仍進頁、各請求相繼 401」的體驗跳動。</p>
   */
  isTokenValid(): boolean {
    if (!this.isAuthenticated()) {
      return false;
    }
    const exp = this.getCurrentUser()?.exp;
    if (exp == null) {
      return true;
    }
    // exp 為秒級；留 0 秒緩衝。可在此加提前續杯的緩衝（如 -5s）視需求調整。
    return exp * 1000 > Date.now();
  }

  /**
   * 集中導向登入頁，並帶上 returnUrl query，且避免「已在登入頁」的重複導向。
   *
   * <p>returnUrl 傳入要記住的頁面（guard 用 state.url、interceptor 用當前 router.url）；
   * 未傳時回退用當前 router.url。登入成功後由 login.component 讀回並導回原頁；
   * 若目標已是登入頁則不再導向（避免 guard/interceptor 迴圈）。</p>
   */
  redirectToLogin(returnUrl?: string): void {
    const current = this.router.url;
    if (current.startsWith(this.loginUrl)) {
      // 已在登入頁（含帶 query）：不重複導向。
      return;
    }
    const target = returnUrl ?? current;
    const safeReturnUrl = target.startsWith('/admin') ? target : '';
    this.router.navigate([this.loginUrl], safeReturnUrl ? { queryParams: { returnUrl: safeReturnUrl } } : {});
  }

  private hasToken(): boolean {
    return this.accessToken !== null;
  }

  /**
   * 解 JWT payload 取 exp（秒級）。不驗簽 —— 簽名由後端把關，前端只讀過期時間
   * 作為 UX 層的「提前攔截」。解析失敗（非 JWT／格式異常）回傳 null。
   */
  private decodeExp(token: string): number | null {
    try {
      const payload = token.split('.')[1];
      if (!payload) {
        return null;
      }
      // base64url → base64，並補齊 padding；atob 需要標準 base64。
      const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
      const json = decodeURIComponent(
        atob(normalized)
          .split('')
          .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      const exp = JSON.parse(json).exp;
      return typeof exp === 'number' ? exp : null;
    } catch {
      return null;
    }
  }
}
