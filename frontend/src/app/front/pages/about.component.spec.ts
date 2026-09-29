import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AboutComponent } from './about.component';

describe('AboutComponent', () => {
  let fixture: ComponentFixture<AboutComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AboutComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(AboutComponent);
    fixture.detectChanges();
  });

  it('應建立元件', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('應顯示關於我們頁面標題', () => {
    const heading = fixture.nativeElement.querySelector('h1');
    expect(heading.textContent).toContain('關於我們');
  });

  it('應列出製作過程的三個步驟', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('精選原料');
    expect(text).toContain('低溫皂化');
    expect(text).toContain('熟成靜置');
  });
});
