import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { EssentialOilsComponent } from './essential-oils.component';
import { ApiService } from '../../shared/services/api.service';
import {
  makeEssentialOil,
  makePage,
  mockApiService
} from '../../testing/test-helpers';

describe('EssentialOilsComponent', () => {
  let fixture: ComponentFixture<EssentialOilsComponent>;
  let component: EssentialOilsComponent;
  let api: jasmine.SpyObj<ApiService>;

  beforeEach(async () => {
    api = mockApiService();
    await TestBed.configureTestingModule({
      imports: [EssentialOilsComponent],
      providers: [provideRouter([]), { provide: ApiService, useValue: api }]
    }).compileComponents();

    fixture = TestBed.createComponent(EssentialOilsComponent);
    component = fixture.componentInstance;
  });

  describe('載入列表', () => {
    it('應以 page/size/sort 參數呼叫 essential-oils 端點', () => {
      api.getPage.and.returnValue(of(makePage([makeEssentialOil()])));

      component.ngOnInit();

      const params = api.getPage.calls.mostRecent().args[1];
      expect(params?.get('page')).toBe('0');
      expect(params?.get('size')).toBe('8');
      expect(params?.get('sort')).toBe('id,desc');
    });

    it('載入成功後應寫入內容與分頁狀態', () => {
      const list = [makeEssentialOil({ id: 1 }), makeEssentialOil({ id: 2 })];
      api.getPage.and.returnValue(of(makePage(list, { number: 1, totalPages: 4 })));

      component.ngOnInit();

      expect(component.essentialOils.length).toBe(2);
      expect(component.page).toBe(1);
      expect(component.totalPages).toBe(4);
    });

    it('請求失敗時應顯示錯誤並可重試，而非餵假資料', () => {
      api.getPage.and.returnValue(throwError(() => new Error('network')));

      component.ngOnInit();

      expect(component.essentialOils).toEqual([]);
      expect(component.errorMessage).toBeTruthy();
    });
  });

  describe('分頁', () => {
    beforeEach(() => {
      api.getPage.and.returnValue(
        of(makePage([makeEssentialOil()], { totalPages: 3, number: 0 }))
      );
      component.ngOnInit();
      api.getPage.calls.reset();
    });

    it('goToPage 應以指定頁碼重新載入', () => {
      component.goToPage(1);

      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('1');
    });

    it('goToPage 對負數或超界應忽略', () => {
      component.goToPage(-1);
      component.goToPage(99);

      expect(api.getPage).not.toHaveBeenCalled();
    });

    it('nextPage / prevPage 應依目前頁位移', () => {
      component.nextPage();
      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('1');

      component.page = 2;
      component.prevPage();
      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('1');
    });

    it('firstPage 與 lastPage 應分別前往首頁與末頁', () => {
      component.page = 1;
      component.firstPage();
      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('0');

      component.lastPage();
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
      component.totalPages = 3;

      expect(component.getPageNumbers()).toEqual([1, 2, 3]);
    });

    it('總頁數多於 7 時應插入省略號', () => {
      component.totalPages = 15;
      component.page = 7;

      expect(component.getPageNumbers()).toContain('...');
    });
  });

  it('應渲染精油卡片', () => {
    api.getPage.and.returnValue(of(makePage([makeEssentialOil({ name: '檸檬精油' })])));

    component.ngOnInit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('檸檬精油');
  });
});
