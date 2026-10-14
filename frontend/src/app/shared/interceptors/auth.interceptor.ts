import { HttpInterceptorFn, HttpErrorResponse, HttpRequest, HttpHandlerFn, HttpEvent } from '@angular/common/http';
import { inject } from '@angular/core';
import { BehaviorSubject, Observable, catchError, filter, switchMap, take, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * 認證攔截器（M8 Phase 2）。
 *
 * <p>每道請求：記憶體 access token → Authorization header；
 * 全請求加 withCredentials 讓瀏覽器跨站帶 refresh Cookie
 *（localhost:4200 → localhost:8080）。</p>
 *
 * <p>401 處理：先打 /auth/refresh 續杯（單飛：併發 401 共用一次 refresh），
 * 成功則重送原請求；refresh 也 401 才 logout 導登入頁。
 * /auth/login、/auth/refresh、/auth/logout 本身 401 不續杯（避免無限遞迴）。</p>
 */
let refreshInFlight = false;
let refreshSubject: BehaviorSubject<string | null> | null = null;

function isAuthEndpoint(req: HttpRequest<unknown>): boolean {
  return req.url.includes('/auth/login')
    || req.url.includes('/auth/refresh')
    || req.url.includes('/auth/logout');
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  const token = authService.getToken();
  let outReq = req.clone({ withCredentials: true });
  if (token) {
    outReq = outReq.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }

  return next(outReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || isAuthEndpoint(req)) {
        // 非 401、或認證端點自己 401（帳密錯／refresh 失效／匿名 logout）
        // 不續杯；若是 refresh 失效由呼叫端（handle401）統一 logout。
        if (error.status === 401 && req.url.includes('/auth/refresh')) {
          // logout() 內部已集中導向登入頁（帶 returnUrl、避免重複導向），
          // 不再額外 navigate。
          authService.logout();
        }
        return throwError(() => error);
      }
      return handle401(req, next, authService);
    })
  );
};

function handle401(
  failedReq: HttpRequest<unknown>,
  next: HttpHandlerFn,
  authService: AuthService
): Observable<HttpEvent<unknown>> {
  if (!refreshInFlight) {
    refreshInFlight = true;
    refreshSubject = new BehaviorSubject<string | null>(null);

    return authService.refresh().pipe(
      switchMap((res) => {
        refreshInFlight = false;
        const newToken = res.accessToken ?? null;
        refreshSubject!.next(newToken);
        refreshSubject!.complete();
        if (!newToken) {
          // logout() 內部已導向登入頁（帶 returnUrl），不需再 navigate。
          authService.logout();
          return throwError(() => new Error('Refresh failed'));
        }
        const retry = failedReq.clone({
          withCredentials: true,
          setHeaders: { Authorization: `Bearer ${newToken}` }
        });
        return next(retry);
      }),
      catchError((err) => {
        refreshInFlight = false;
        refreshSubject!.error(err);
        refreshSubject = null;
        // logout() 內部已導向登入頁（帶 returnUrl），不需再 navigate。
        authService.logout();
        return throwError(() => err);
      })
    );
  }

  // 已有 refresh 在飛：等它完成後重送（成功）或跟著失敗（refresh 掛了就一起登出）。
  return refreshSubject!.pipe(
    filter((t): t is string => t !== null),
    take(1),
    switchMap((newToken) => next(failedReq.clone({
      withCredentials: true,
      setHeaders: { Authorization: `Bearer ${newToken}` }
    })))
  );
}
