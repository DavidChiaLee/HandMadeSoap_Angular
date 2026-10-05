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
  private tokenKey = 'auth_token';
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
          this.setToken(response.accessToken);
          this.setUser(response);
        }
        return response;
      })
    );
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.userKey);
    this.isAuthenticatedSubject.next(false);
    this.currentUserSubject.next(null);
    this.router.navigate(['/admin/login']);
  }

  setToken(token: string): void {
    localStorage.setItem(this.tokenKey, token);
    this.isAuthenticatedSubject.next(true);
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
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
    return !!localStorage.getItem(this.tokenKey);
  }
}
