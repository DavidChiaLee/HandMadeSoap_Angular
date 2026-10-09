import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OilsComponent } from './oils.component';
import { ApiService } from '../../shared/services/api.service';
import { makeOil, makePage, mockApiService } from '../../testing/test-helpers';

describe('OilsComponent', () => {
  let fixture: ComponentFixture<OilsComponent>;
  let component: OilsComponent;
  let api: jasmine.SpyObj<ApiService>;

  beforeEach(async () => {
    api = mockApiService();
    await TestBed.configureTestingModule({
      imports: [OilsComponent],
      providers: [provideRouter([]), { provide: ApiService, useValue: api }]
    }).compileComponents();

    fixture = TestBed.createComponent(OilsComponent);
    component = fixture.componentInstance;
  });

  describe('載入列表', () => {
    it('應以 page/size/sort 參數呼叫 oils 端點', () => {
      api.getPage.and.returnValue(of(makePage([makeOil()])));

      component.ngOnInit();

      const params = api.getPage.calls.mostRecent().args[1];
      expect(params?.get('page')).toBe('0');
      expect(params?.get('size')).toBe('8');
      expect(params?.get('sort')).toBe('id,desc');
    });

    it('載入成功後應寫入內容與分頁狀態', () => {
      const list = [makeOil({ id: 1 }), makeOil({ id: 2 })];
      api.getPage.and.returnValue(of(makePage(list, { number: 2, totalPages: 5 })));

      component.ngOnInit();

      expect(component.oils.length).toBe(2);
      expect(component.page).toBe(2);
      expect(component.totalPages).toBe(5);
    });

    it('請求失敗時應顯示錯誤並可重試，而非餵假資料', () => {
      api.getPage.and.returnValue(throwError(() => new Error('network')));

      component.ngOnInit();

      expect(component.oils).toEqual([]);
      expect(component.errorMessage).toBeTruthy();
    });
  });

  describe('分頁', () => {
    beforeEach(() => {
      api.getPage.and.returnValue(of(makePage([makeOil()], { totalPages: 5, number: 0 })));
      component.ngOnInit();
      api.getPage.calls.reset();
    });

    it('goToPage 應以指定頁碼重新載入', () => {
      component.goToPage(2);

      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('2');
    });

    it('goToPage 對負數應忽略', () => {
      component.goToPage(-1);

      expect(api.getPage).not.toHaveBeenCalled();
    });

    it('goToPage 對超過總頁數應忽略', () => {
      component.goToPage(99);

      expect(api.getPage).not.toHaveBeenCalled();
    });

    it('goToPage 對目前所在頁應不重複請求', () => {
      component.goToPage(0);

      expect(api.getPage).not.toHaveBeenCalled();
    });

    it('nextPage 應前往下一頁', () => {
      component.nextPage();

      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('1');
    });

    it('prevPage 應前往上一頁', () => {
      component.page = 2;
      component.prevPage();

      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('1');
    });

    it('firstPage 應回到第 0 頁', () => {
      component.page = 3;
      component.firstPage();

      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('0');
    });

    it('lastPage 應前往最後一頁', () => {
      component.lastPage();

      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('4');
    });

    it('onPageNumberClick 收到數字時應切換到對應頁（1-based）', () => {
      component.onPageNumberClick(3);

      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('2');
    });

    it('onPageNumberClick 收到省略號時不應發出請求', () => {
      component.onPageNumberClick('...');

      expect(api.getPage).not.toHaveBeenCalled();
    });
  });

  describe('頁碼列表', () => {
    it('總頁數為 0 時應回傳空陣列', () => {
      component.totalPages = 0;

      expect(component.getPageNumbers()).toEqual([]);
    });

    it('總頁數小於等於 7 時應列出所有頁碼', () => {
      component.totalPages = 5;
      component.page = 0;

      expect(component.getPageNumbers()).toEqual([1, 2, 3, 4, 5]);
    });

    it('總頁數多於 7 時應在中間插入省略號', () => {
      component.totalPages = 20;
      component.page = 9;

      const pages = component.getPageNumbers();

      expect(pages).toContain('...');
      expect(pages[0]).toBe(1);
      expect(pages[pages.length - 1]).toBe(20);
    });

    it('目前頁在開頭時應壓縮中間頁碼並插入省略號', () => {
      component.totalPages = 20;
      component.page = 0;

      // totalPages > 7 時只保留首尾與鄰近頁，中間以省略號表示
      expect(component.getPageNumbers()).toEqual([1, 2, '...', 19, 20]);
    });

    it('頁碼不應超出有效範圍', () => {
      component.totalPages = 20;
      component.page = 0;

      const inRange = component
        .getPageNumbers()
        .filter((n): n is number => typeof n === 'number')
        .every(n => n >= 1 && n <= 20);

      expect(inRange).toBeTrue();
    });
  });

  it('應渲染油品卡片', () => {
    api.getPage.and.returnValue(of(makePage([makeOil({ name: '甜杏仁油' })])));

    component.ngOnInit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('甜杏仁油');
  });
});
