import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../shared/services/auth.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['login']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('應建立元件', () => {
    expect(component).toBeTruthy();
  });

  it('初始帳密應為空字串且無錯誤訊息', () => {
    expect(component.credentials).toEqual({ username: '', password: '' });
    expect(component.errorMessage).toBe('');
  });

  it('送出時應將輸入的帳密交給 AuthService', () => {
    authService.login.and.returnValue(of({} as any));
    component.credentials = { username: 'admin', password: 'secret' };

    component.onSubmit();

    expect(authService.login).toHaveBeenCalledWith({ username: 'admin', password: 'secret' });
  });

  it('登入成功後應導向儀表板', () => {
    authService.login.and.returnValue(of({} as any));
    component.credentials = { username: 'admin', password: 'secret' };

    component.onSubmit();

    expect(router.navigate).toHaveBeenCalledWith(['/admin/dashboard']);
  });

  it('登入失敗時應顯示後端回傳的訊息', () => {
    authService.login.and.returnValue(
      throwError(() => ({ error: { message: 'Invalid username or password' } }))
    );
    component.credentials = { username: 'a', password: 'b' };

    component.onSubmit();

    expect(component.errorMessage).toBe('Invalid username or password');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('後端未回傳訊息時應顯示預設文案', () => {
    authService.login.and.returnValue(throwError(() => ({})));

    component.onSubmit();

    expect(component.errorMessage).toBe('登入失敗，請檢查您的帳號密碼');
  });

  it('登入失敗時應將錯誤訊息顯示於畫面', () => {
    authService.login.and.returnValue(
      throwError(() => ({ error: { message: '帳號已被鎖定' } }))
    );

    component.onSubmit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('帳號已被鎖定');
  });

  it('再次送出前應清空先前的錯誤訊息', () => {
    authService.login.and.returnValue(throwError(() => ({ error: { message: '密碼錯誤' } })));
    component.onSubmit();
    expect(component.errorMessage).toBe('密碼錯誤');

    authService.login.and.returnValue(of({} as any));
    component.onSubmit();

    expect(component.errorMessage).toBe('');
  });
});
