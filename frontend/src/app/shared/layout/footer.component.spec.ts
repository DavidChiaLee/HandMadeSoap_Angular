import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FooterComponent } from './footer.component';

describe('FooterComponent', () => {
  let fixture: ComponentFixture<FooterComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FooterComponent],
      providers: [provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(FooterComponent);
    fixture.detectChanges();
  });

  it('應建立元件', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('應渲染 footer 元素', () => {
    expect(fixture.nativeElement.querySelector('footer')).toBeTruthy();
  });

  it('應提供首頁與聯絡我們等快速連結', () => {
    const links = Array.from<HTMLAnchorElement>(
      fixture.nativeElement.querySelectorAll('a')
    ).map(a => a.getAttribute('href'));

    expect(links).toContain('/');
    expect(links).toContain('/soaps');
    expect(links).toContain('/contact');
  });
});
