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
});
