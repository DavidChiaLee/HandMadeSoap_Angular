import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AppComponent } from './app.component';
import { AuthService } from './shared/services/auth.service';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        // app-navbar 會注入 AuthService（顯示後台登入入口 / 使用者名稱），
        // 這裡以最小 stub 取代，避免為了測根元件而拉進 ApiService / HttpClient。
        {
          provide: AuthService,
          useValue: { isAuthenticated$: of(false), currentUser$: of(null) }
        }
      ]
    }).compileComponents();
  });

  it('應建立根元件', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('應同時渲染導覽列、內容出口與頁尾', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement;

    expect(el.querySelector('app-navbar')).toBeTruthy();
    expect(el.querySelector('router-outlet')).toBeTruthy();
    expect(el.querySelector('app-footer')).toBeTruthy();
  });
});
