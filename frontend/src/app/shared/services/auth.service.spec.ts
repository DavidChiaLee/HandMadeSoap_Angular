import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { ApiService } from './api.service';
import { LoginResponse } from '../models/login.model';
import { mockApiService } from '../../testing/test-helpers';

describe('AuthService', () => {
  let service: AuthService;
  let api: jasmine.SpyObj<ApiService>;
  let router: jasmine.SpyObj<Router>;

  const tokenKey = 'auth_token';
  const userKey = 'current_user';

  const loginResponse: LoginResponse = {
    accessToken: 'jwt-token',
    refreshToken: '',
    username: 'admin',
    role: 'ADMIN'
  };

  beforeEach(() => {
    localStorage.clear();
    api = mockApiService();
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        { provide: ApiService, useValue: api },
        { provide: Router, useValue: router }
      ]
    });
    service = TestBed.inject(AuthService);
  });

  afterEach(() => localStorage.clear());

  it('登入成功時應將 token 與使用者寫入 localStorage', () => {
    api.post.and.returnValue(of(loginResponse));

    service.login({ username: 'admin', password: 'secret' }).subscribe();

    expect(api.post).toHaveBeenCalledWith('auth/login', { username: 'admin', password: 'secret' });
    expect(localStorage.getItem(tokenKey)).toBe('jwt-token');
    expect(JSON.parse(localStorage.getItem(userKey)!).username).toBe('admin');
  });

  it('登入成功時應將 isAuthenticated$ 發出 true', () => {
    api.post.and.returnValue(of(loginResponse));
    const states: boolean[] = [];
    service.isAuthenticated$.subscribe(v => states.push(v));

    service.login({ username: 'admin', password: 'secret' }).subscribe();

    expect(states[states.length - 1]).toBeTrue();
    expect(service.isAuthenticated()).toBeTrue();
  });

  it('回應缺少 accessToken 時不應寫入 token', () => {
    api.post.and.returnValue(of({ ...loginResponse, accessToken: '' }));

    service.login({ username: 'a', password: 'b' }).subscribe();

    expect(localStorage.getItem(tokenKey)).toBeNull();
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('登入失敗時應將錯誤往外傳遞且不寫入 token', () => {
    api.post.and.returnValue(throwError(() => ({ error: { message: '帳號或密碼錯誤' } })));
    let caught: any = null;

    service.login({ username: 'a', password: 'bad' }).subscribe({ error: e => caught = e });

    expect(caught).toBeTruthy();
    expect(localStorage.getItem(tokenKey)).toBeNull();
  });

  it('setToken 應寫入 localStorage 並更新登入狀態', () => {
    service.setToken('abc');

    expect(localStorage.getItem(tokenKey)).toBe('abc');
    expect(service.isAuthenticated()).toBeTrue();
  });

  it('getToken 在未登入時應回傳 null', () => {
    expect(service.getToken()).toBeNull();
  });

  it('getCurrentUser 應還原存取的登入者資料', () => {
    service.setUser(loginResponse);

    expect(service.getCurrentUser().role).toBe('ADMIN');
  });

  it('getCurrentUser 在未曾登入時應回傳 null', () => {
    expect(service.getCurrentUser()).toBeNull();
  });

  it('logout 應清除 token 與使用者並導向登入頁', () => {
    service.setToken('abc');
    service.setUser(loginResponse);
    const states: boolean[] = [];
    service.isAuthenticated$.subscribe(v => states.push(v));

    service.logout();

    expect(localStorage.getItem(tokenKey)).toBeNull();
    expect(localStorage.getItem(userKey)).toBeNull();
    expect(states[states.length - 1]).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/admin/login']);
  });

  it('currentUser$ 應在登入後發出使用者資料（含 username）', () => {
    // 必須 stub 回應帶 username：mockApiService 的預設回傳 of({})，
    // 沒有 accessToken 就不會呼叫 setUser()，測試會因空資料而失敗。
    api.post.and.returnValue(of(loginResponse));
    const users: any[] = [];
    service.currentUser$.subscribe((u: any) => users.push(u));

    service.login({ username: 'admin', password: 'secret' }).subscribe();

    // navbar 顯示使用者名稱靠的就是這個欄位（後端沒有 name）
    expect(users[users.length - 1]).not.toBeNull();
    expect(users[users.length - 1].username).toBe('admin');
  });

  it('currentUser$ 應在 logout 後發出 null', () => {
    service.setUser(loginResponse);
    const users: any[] = [];
    service.currentUser$.subscribe(u => users.push(u));

    service.logout();

    expect(users[users.length - 1]).toBeNull();
  });

  it('currentUser$ 應以 localStorage 既有資料初始化（重新整理後仍正確）', () => {
    localStorage.setItem(tokenKey, 'jwt-token');
    localStorage.setItem(userKey, JSON.stringify(loginResponse));

    // 重建 service 模擬頁面重新整理；constructor 會讀 localStorage 作為初始值
    const fresh = new AuthService(api, router);

    let emitted: any = undefined;
    fresh.currentUser$.subscribe((u: any) => emitted = u);

    expect(emitted.username).toBe('admin');
  });
});
