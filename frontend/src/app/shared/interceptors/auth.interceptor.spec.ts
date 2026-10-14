import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authService = jasmine.createSpyObj<AuthService>('AuthService', [
      'getToken',
      'logout',
      'refresh'
    ]);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    // refresh 預設回報成功（續杯），避免干擾非 refresh 相關案例。
    authService.refresh.and.returnValue(of({ accessToken: 'new-token' } as any));

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router }
      ]
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('有 token 時應附加 Authorization: Bearer header', () => {
    authService.getToken.and.returnValue('jwt-token');

    http.get('/api/v1/admin/soaps').subscribe();

    const req = httpMock.expectOne('/api/v1/admin/soaps');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    req.flush({});
  });

  it('無 token 時不應附加 Authorization header', () => {
    authService.getToken.and.returnValue(null);

    http.get('/api/v1/soaps').subscribe();

    const req = httpMock.expectOne('/api/v1/soaps');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('收到 401 時應先 refresh 續杯，成功則以新 token 重送（不登出）', () => {
    authService.getToken.and.returnValue('jwt-token');
    authService.refresh.and.returnValue(of({ accessToken: 'new-token' } as any));

    http.get('/api/v1/admin/soaps').subscribe();

    httpMock
      .expectOne('/api/v1/admin/soaps')
      .flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    // refresh 續杯成功 → 重送原請求（帶新 token），不登出。
    const retry = httpMock.expectOne('/api/v1/admin/soaps');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new-token');
    retry.flush({});

    expect(authService.refresh).toHaveBeenCalled();
    expect(authService.logout).not.toHaveBeenCalled();
  });

  it('refresh 也失效時應登出（導向登入頁由 logout 內部處理，不再重複 navigate）', () => {
    authService.getToken.and.returnValue('jwt-token');
    authService.refresh.and.returnValue(throwError(() => ({ status: 401 })));
    let caught: any = null;

    http.get('/api/v1/admin/soaps').subscribe({ error: e => caught = e });

    httpMock
      .expectOne('/api/v1/admin/soaps')
      .flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authService.logout).toHaveBeenCalled();
    // interceptor 不再自行 navigate（避免與 logout 重複導向）。
    expect(router.navigate).not.toHaveBeenCalled();
    expect(caught).toBeTruthy();
  });

  it('收到 403 時不應登出（僅權限不足）', () => {
    authService.getToken.and.returnValue('jwt-token');

    http.get('/api/v1/admin/soaps').subscribe({ error: () => undefined });

    httpMock
      .expectOne('/api/v1/admin/soaps')
      .flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

    expect(authService.logout).not.toHaveBeenCalled();
  });

  it('收到 500 時應原樣往外傳遞錯誤', () => {
    authService.getToken.and.returnValue('jwt-token');
    let caught: any = null;

    http.get('/api/v1/admin/soaps').subscribe({ error: e => caught = e });

    httpMock
      .expectOne('/api/v1/admin/soaps')
      .flush({ message: 'Boom' }, { status: 500, statusText: 'Server Error' });

    expect(authService.logout).not.toHaveBeenCalled();
    expect(caught.status).toBe(500);
  });

  it('請求成功時不應觸發任何登出行為', () => {
    authService.getToken.and.returnValue('jwt-token');

    http.get('/api/v1/soaps').subscribe();

    httpMock.expectOne('/api/v1/soaps').flush({});

    expect(authService.logout).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
