import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AdminLayoutComponent } from './admin-layout.component';
import { AuthService } from '../shared/services/auth.service';

describe('AdminLayoutComponent', () => {
  let authService: jasmine.SpyObj<AuthService>;

  const build = () => {
    const fixture = TestBed.createComponent(AdminLayoutComponent);
    return fixture;
  };

  beforeEach(async () => {
    authService = jasmine.createSpyObj<AuthService>('AuthService', [
      'getCurrentUser',
      'logout'
    ]);

    await TestBed.configureTestingModule({
      imports: [AdminLayoutComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: authService }]
    }).compileComponents();
  });

  it('應建立元件', () => {
    expect(build().componentInstance).toBeTruthy();
  });

  it('應從 AuthService 取得目前登入者', () => {
    authService.getCurrentUser.and.returnValue({ username: 'admin', role: 'ADMIN' });

    const component = build().componentInstance;

    expect(authService.getCurrentUser).toHaveBeenCalled();
    expect(component.currentUser.username).toBe('admin');
  });

  it('未登入時 currentUser 應為 null', () => {
    authService.getCurrentUser.and.returnValue(null);

    expect(build().componentInstance.currentUser).toBeNull();
  });

  it('logout 應委派給 AuthService 處理', () => {
    authService.getCurrentUser.and.returnValue(null);
    const component = build().componentInstance;

    component.logout();

    expect(authService.logout).toHaveBeenCalled();
  });

  it('應提供前往各管理頁面的導覽連結', () => {
    authService.getCurrentUser.and.returnValue(null);
    const fixture = build();
    fixture.detectChanges();

    const hrefs = Array.from<HTMLAnchorElement>(
      fixture.nativeElement.querySelectorAll('a')
    ).map(a => a.getAttribute('href'));

    expect(hrefs).toContain('/admin/dashboard');
    expect(hrefs).toContain('/admin/categories');
    expect(hrefs).toContain('/admin/soaps');
    expect(hrefs).toContain('/admin/contacts');
  });
});
