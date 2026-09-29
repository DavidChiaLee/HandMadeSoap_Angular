import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;
  let runGuard: () => boolean;

  beforeEach(() => {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['isAuthenticated']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    runGuard = () =>
      TestBed.runInInjectionContext(() => authGuard({} as any, {} as any)) as boolean;

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router }
      ]
    });
  });

  it('已登入時應允許進入', () => {
    authService.isAuthenticated.and.returnValue(true);

    expect(runGuard()).toBeTrue();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('未登入時應導向登入頁並拒絕進入', () => {
    authService.isAuthenticated.and.returnValue(false);

    expect(runGuard()).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/admin/login']);
  });
});
