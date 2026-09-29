import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NavbarComponent } from './navbar.component';

describe('NavbarComponent', () => {
  let fixture: ComponentFixture<NavbarComponent>;
  let component: NavbarComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NavbarComponent],
      providers: [provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(NavbarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

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

  it('應渲染前往後台登入的連結', () => {
    const links = Array.from<HTMLAnchorElement>(
      fixture.nativeElement.querySelectorAll('a')
    ).map(a => a.getAttribute('href'));

    expect(links).toContain('/admin/login');
  });
});
