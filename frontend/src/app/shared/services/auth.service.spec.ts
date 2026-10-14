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

  const userKey = 'current_user';

  const loginResponse: LoginResponse = {
    accessToken: 'jwt-token',
    refreshToken: '',
    username: 'admin',
    role: 'ADMIN'
  };

  // 以 header.payload.signature 結構組一個含 exp 的假 JWT（payload 為 base64url）。
  const makeJwt = (payload: object): string => {
    const enc = (o: object) =>
      btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    return `${enc({ alg: 'HS256', typ: 'JWT' })}.${enc(payload)}.sig`;
  };

  beforeEach(() => {
    localStorage.clear();
    api = mockApiService();
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    // redirectToLogin 讀 router.url；預設在 admin 頁面（非登入頁）。
    Object.defineProperty(router, 'url', { value: '/admin/soaps', configurable: true });

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

  it('登入成功時應將使用者資料（含 exp）寫入 localStorage', () => {
    const exp = Math.floor(Date.now() / 1000) + 900;
    api.post.and.returnValue(of({ ...loginResponse, accessToken: makeJwt({ exp }) }));

    service.login({ username: 'admin', password: 'secret' }).subscribe();

    expect(api.post).toHaveBeenCalledWith('auth/login', { username: 'admin', password: 'secret' });
    const saved = JSON.parse(localStorage.getItem(userKey)!);
    expect(saved.username).toBe('admin');
    expect(saved.role).toBe('ADMIN');
    expect(saved.exp).toBe(exp);
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

    expect(service.isAuthenticated()).toBeFalse();
    expect(localStorage.getItem(userKey)).toBeNull();
  });

  it('accessToken 非 JWT 時 exp 存為 null（解碼失敗不中斷登入）', () => {
    api.post.and.returnValue(of(loginResponse));

    service.login({ username: 'admin', password: 'secret' }).subscribe();

    expect(JSON.parse(localStorage.getItem(userKey)!).exp).toBeNull();
  });

  it('登入失敗時應將錯誤往外傳遞且不寫入 token', () => {
    api.post.and.returnValue(throwError(() => ({ error: { message: '帳號或密碼錯誤' } })));
    let caught: any = null;

    service.login({ username: 'a', password: 'bad' }).subscribe({ error: e => caught = e });

    expect(caught).toBeTruthy();
    expect(localStorage.getItem(userKey)).toBeNull();
  });

  it('setToken 應更新登入狀態', () => {
    service.setToken('abc');

    expect(service.getToken()).toBe('abc');
    expect(service.isAuthenticated()).toBeTrue();
  });

  it('getToken 在未登入時應回傳 null', () => {
    expect(service.getToken()).toBeNull();
  });

  it('getCurrentUser 應還原存取的登入者資料', () => {
    service.setUser(loginResponse);

    expect(service.getCurrentUser()!.role).toBe('ADMIN');
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

    expect(service.getToken()).toBeNull();
    expect(localStorage.getItem(userKey)).toBeNull();
    expect(states[states.length - 1]).toBeFalse();
    expect(router.navigate).toHaveBeenCalled();
  });

  it('logout 應通知後端撤銷 token（review M8 Phase 1）', () => {
    service.setToken('abc');

    service.logout();

    // 必須在清除 localStorage 之前發出，攔截器才取得到 Bearer header
    expect(api.post).toHaveBeenCalledWith('auth/logout', {});
  });

  it('未登入時 logout 不應呼叫後端（避免匿名打 401）', () => {
    service.logout();

    expect(api.post).not.toHaveBeenCalled();
  });

  it('後端撤銷失敗時仍應完成本機登出（best-effort，review M8 Phase 1）', () => {
    service.setToken('abc');
    service.setUser(loginResponse);
    api.post.and.returnValue(throwError(() => new Error('network down')));

    expect(() => service.logout()).not.toThrow();

    expect(service.getToken()).toBeNull();
    expect(localStorage.getItem(userKey)).toBeNull();
    expect(router.navigate).toHaveBeenCalled();
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
    localStorage.setItem(userKey, JSON.stringify(loginResponse));

    // 重建 service 模擬頁面重新整理；constructor 會讀 localStorage 作為初始值
    const fresh = new AuthService(api, router);

    let emitted: any = undefined;
    fresh.currentUser$.subscribe((u: any) => emitted = u);

    expect(emitted.username).toBe('admin');
  });

  describe('isTokenValid', () => {
    it('未登入時應為 false', () => {
      expect(service.isTokenValid()).toBeFalse();
    });

    it('exp 在未來時應為 true', () => {
      service.setToken('t');
      service.setUser({ username: 'a', role: 'ADMIN', exp: Math.floor(Date.now() / 1000) + 900 });

      expect(service.isTokenValid()).toBeTrue();
    });

    it('exp 已過期時應為 false', () => {
      service.setToken('t');
      service.setUser({ username: 'a', role: 'ADMIN', exp: Math.floor(Date.now() / 1000) - 10 });

      expect(service.isTokenValid()).toBeFalse();
    });

    it('exp 缺漏時從寬視為有效（真正的把關在後端）', () => {
      service.setToken('t');
      service.setUser({ username: 'a', role: 'ADMIN' });

      expect(service.isTokenValid()).toBeTrue();
    });
  });

  describe('redirectToLogin', () => {
    it('在 admin 頁面時應導向登入頁並帶 returnUrl', () => {
      Object.defineProperty(router, 'url', { value: '/admin/soaps', configurable: true });

      service.redirectToLogin();

      expect(router.navigate).toHaveBeenCalledWith(['/admin/login'], {
        queryParams: { returnUrl: '/admin/soaps' }
      });
    });

    it('非 admin 頁面時應導向登入頁但不帶 returnUrl', () => {
      Object.defineProperty(router, 'url', { value: '/soaps', configurable: true });

      service.redirectToLogin();

      expect(router.navigate).toHaveBeenCalledWith(['/admin/login'], {});
    });

    it('已在登入頁時不應重複導向', () => {
      Object.defineProperty(router, 'url', { value: '/admin/login?returnUrl=/admin/soaps', configurable: true });

      service.redirectToLogin();

      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('傳入的 returnUrl 優先於 router.url（guard 用 state.url）', () => {
      Object.defineProperty(router, 'url', { value: '/admin/soaps', configurable: true });

      service.redirectToLogin('/admin/categories');

      expect(router.navigate).toHaveBeenCalledWith(['/admin/login'], {
        queryParams: { returnUrl: '/admin/categories' }
      });
    });
  });
});
