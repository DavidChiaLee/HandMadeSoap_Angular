import { TestBed } from '@angular/core/testing';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let runGuard: (url?: string) => boolean;

  beforeEach(() => {
    authService = jasmine.createSpyObj<AuthService>('AuthService', [
      'isTokenValid',
      'getCurrentUser',
      'redirectToLogin'
    ]);
    // 預設為有效且 ADMIN，代表可進入。
    authService.isTokenValid.and.returnValue(true);
    authService.getCurrentUser.and.returnValue({ username: 'admin', role: 'ADMIN' });
    runGuard = (url = '/admin/soaps') =>
      TestBed.runInInjectionContext(() => authGuard({} as any, { url } as any)) as boolean;

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService }
      ]
    });
  });

  it('有效 token 且 role 為 ADMIN 時應允許進入', () => {
    authService.isTokenValid.and.returnValue(true);
    authService.getCurrentUser.and.returnValue({ username: 'admin', role: 'ADMIN' });

    expect(runGuard()).toBeTrue();
    expect(authService.redirectToLogin).not.toHaveBeenCalled();
  });

  it('未登入／token 無效時應導向登入頁（帶 state.url）並拒絕進入', () => {
    authService.isTokenValid.and.returnValue(false);

    expect(runGuard('/admin/soaps')).toBeFalse();
    expect(authService.redirectToLogin).toHaveBeenCalledWith('/admin/soaps');
  });

  it('token 已過期時應被攔下', () => {
    // isTokenValid 已內含 exp 判斷；過期時回 false。
    authService.isTokenValid.and.returnValue(false);
    authService.getCurrentUser.and.returnValue({ username: 'admin', role: 'ADMIN' });

    expect(runGuard()).toBeFalse();
    expect(authService.redirectToLogin).toHaveBeenCalled();
  });

  it('role 非 ADMIN 時即使 token 有效也應被攔下', () => {
    authService.isTokenValid.and.returnValue(true);
    authService.getCurrentUser.and.returnValue({ username: 'user', role: 'USER' });

    expect(runGuard()).toBeFalse();
    expect(authService.redirectToLogin).toHaveBeenCalled();
  });
});
