import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { NavbarComponent } from './navbar.component';
import { AuthService } from '../services/auth.service';

describe('NavbarComponent', () => {
  let fixture: ComponentFixture<NavbarComponent>;
  let component: NavbarComponent;
  let router: Router;
  let isAuthenticated$: BehaviorSubject<boolean>;
  let currentUser$: BehaviorSubject<any>;

  beforeEach(async () => {
    isAuthenticated$ = new BehaviorSubject<boolean>(false);
    currentUser$ = new BehaviorSubject<any>(null);

    // AuthService 只用到這兩個 stream，因此以最小 stub 取代，
    // 避免為了測 navbar 而把 ApiService / localStorage 一併拉進來。
    const authStub = {
      isAuthenticated$: isAuthenticated$.asObservable(),
      currentUser$: currentUser$.asObservable()
    };

    await TestBed.configureTestingModule({
      imports: [NavbarComponent],
      providers: [
        provideRouter([{ path: '', children: [] }, { path: 'admin/login', children: [] }]),
        { provide: AuthService, useValue: authStub }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(NavbarComponent);
    component = fixture.componentInstance;
    await router.navigateByUrl('/');
    fixture.detectChanges();
  });

  /** 取出目前 template 中所有 <a> 的 href */
  const hrefs = (): (string | null)[] =>
    Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('a'))
      .map(a => a.getAttribute('href'));

  it('應建立元件', () => {
    expect(component).toBeTruthy();
  });

  it('行動版選單預設應為收合', () => {
    expect(component.isMobileMenuOpen).toBeFalse();
  });

  it('toggleMobileMenu 應在開啟與收合之間切換', () => {
    component.toggleMobileMenu();
    expect(component.isMobileMenuOpen).toBeTrue();

    component.toggleMobileMenu();
    expect(component.isMobileMenuOpen).toBeFalse();
  });

  it('點擊漢堡按鈕後應展開行動版選單', () => {
    const button = fixture.nativeElement.querySelector('button');
    button.click();
    fixture.detectChanges();

    expect(component.isMobileMenuOpen).toBeTrue();
  });

  it('前台頁面不應渲染後台登入連結', () => {
    // 前台是給一般訪客看的，不該出現後台入口
    expect(hrefs()).not.toContain('/admin/login');
  });

  it('切換到後台路由後應渲染後台登入連結', async () => {
    await router.navigateByUrl('/admin/login');
    fixture.detectChanges();

    expect(component.isAdminRoute()).toBeTrue();
    expect(hrefs()).toContain('/admin/login');
  });

  it('後台已登入時應顯示使用者名稱而非登入連結', async () => {
    isAuthenticated$.next(true);
    currentUser$.next({ username: 'admin', role: 'ADMIN' });
    await router.navigateByUrl('/admin/login');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('admin');
    expect(hrefs()).not.toContain('/admin/login');
  });

  it('已登入但缺少使用者資料時應顯示兜底文案而非空白', async () => {
    // localStorage 有 token 卻沒有 current_user 的邊界情況
    isAuthenticated$.next(true);
    currentUser$.next(null);
    await router.navigateByUrl('/admin/login');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('已登入');
  });

  it('登出後應恢復顯示後台登入連結', async () => {
    isAuthenticated$.next(true);
    currentUser$.next({ username: 'admin', role: 'ADMIN' });
    await router.navigateByUrl('/admin/login');
    fixture.detectChanges();

    isAuthenticated$.next(false);
    currentUser$.next(null);
    fixture.detectChanges();

    expect(hrefs()).toContain('/admin/login');
  });

  it('回到前台後即使已登入也不應顯示使用者名稱', async () => {
    isAuthenticated$.next(true);
    currentUser$.next({ username: 'admin', role: 'ADMIN' });
    await router.navigateByUrl('/admin/login');
    fixture.detectChanges();

    await router.navigateByUrl('/');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('admin');
    expect(hrefs()).not.toContain('/admin/login');
  });
});
