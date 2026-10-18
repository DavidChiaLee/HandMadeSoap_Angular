import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { of, throwError } from 'rxjs';
import { OilSearchListComponent, OilListConfig } from './oil-search-list.component';
import { ApiService } from '../../shared/services/api.service';
import { makeOil, makeEssentialOil, makePage, mockApiService } from '../../testing/test-helpers';

describe('OilSearchListComponent', () => {
  let fixture: ComponentFixture<OilSearchListComponent>;
  let component: OilSearchListComponent;
  let api: jasmine.SpyObj<ApiService>;

  const oilConfig: OilListConfig = {
    title: '天然油品',
    placeholder: '輸入關鍵字搜尋油品名稱',
    emptyWithKeyword: '查無符合條件的油品',
    emptyWithoutKeyword: '查無油品資料',
    detailField: 'benefits',
    routePrefix: '/oils',
    baseEndpoint: 'oils'
  };

  beforeEach(async () => {
    api = mockApiService();
    await TestBed.configureTestingModule({
      imports: [OilSearchListComponent],
      providers: [provideRouter([]), { provide: ApiService, useValue: api }]
    }).compileComponents();

    fixture = TestBed.createComponent(OilSearchListComponent);
    component = fixture.componentInstance;
    // 模板綁定 config.*，必須在第一次 detectChanges 之前設定
    component.config = oilConfig;
  });

  afterEach(() => fixture?.destroy());

  describe('載入列表', () => {
    it('ngOnInit 應以 page/size/sort 參數呼叫 baseEndpoint', () => {
      component.ngOnInit();
      const [endpoint, params] = api.getPage.calls.mostRecent().args;
      expect(endpoint).toBe('oils');
      expect(params?.get('page')).toBe('0');
      expect(params?.get('size')).toBe('8');
      expect(params?.get('sort')).toBe('id,desc');
      expect(params?.get('keyword')).toBeNull();
    });

    it('載入成功後應寫入內容與分頁狀態', () => {
      api.getPage.and.returnValue(of(makePage([makeOil()], {
        totalPages: 3,
        number: 2,
        totalElements: 25
      })));
      component.ngOnInit();
      expect(component.items.length).toBe(1);
      expect(component.items[0].name).toBe('橄欖油');
      expect(component.page).toBe(2);
      expect(component.totalPages).toBe(3);
      expect(component.searchResultCount).toBe(25);
      expect(component.isLoading).toBeFalse();
      expect(component.errorMessage).toBeNull();
    });

    it('請求失敗時應顯示錯誤並清空列表', () => {
      api.getPage.and.returnValue(throwError(() => new Error('network')));
      component.items = [makeOil()];
      component.totalPages = 3;
      component.ngOnInit();
      expect(component.items).toEqual([]);
      expect(component.totalPages).toBe(0);
      expect(component.errorMessage).toContain('載入天然油品失敗');
      expect(component.isLoading).toBeFalse();
    });
  });

  describe('搜尋', () => {
    it('doSearch 應 trim 關鍵字並呼叫 /search 端點', () => {
      component.searchKeyword = '  橄欖  ';
      component.doSearch();
      const [endpoint, params] = api.getPage.calls.mostRecent().args;
      expect(endpoint).toBe('oils/search');
      expect(params?.get('keyword')).toBe('橄欖');
      expect(params?.get('page')).toBe('0');
      expect(component.searchKeyword).toBe('  橄欖  '); // 輸入框原值保留，供清除按鈕判斷
    });

    it('關鍵字全為空白時應視為無關鍵字', () => {
      component.searchKeyword = '   ';
      component.doSearch();
      const [endpoint, params] = api.getPage.calls.mostRecent().args;
      expect(endpoint).toBe('oils');
      expect(params?.get('keyword')).toBeNull();
    });

    it('搜尋失敗時應顯示搜尋專屬錯誤訊息', () => {
      api.getPage.and.returnValue(throwError(() => new Error('network')));
      component.searchKeyword = '橄欖';
      component.doSearch();
      expect(component.errorMessage).toContain('搜尋失敗，請稍後再試');
      expect(component.items).toEqual([]);
      expect(component.isSearching).toBeFalse();
    });

    it('clearSearch 應清空狀態並重新載入', () => {
      component.searchKeyword = '橄欖';
      component.searchResultCount = 5;
      component.isSearching = true;
      component.clearSearch();
      expect(component.searchKeyword).toBe('');
      expect(component.searchResultCount).toBe(0);
      expect(component.isSearching).toBeFalse();
      const [endpoint, params] = api.getPage.calls.mostRecent().args;
      expect(endpoint).toBe('oils');
      expect(params?.get('page')).toBe('0');
      expect(params?.get('keyword')).toBeNull();
    });
  });

  describe('分頁', () => {
    beforeEach(() => {
      component.totalPages = 3;
      component.page = 0;
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

    it('firstPage 應前往首頁', () => {
      component.page = 2;
      component.firstPage();
      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('0');
    });

    it('prevPage 應往前一頁', () => {
      component.page = 2;
      component.prevPage();
      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('1');
    });

    it('nextPage 應往後一頁', () => {
      component.nextPage();
      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('1');
    });

    it('lastPage 應前往末頁', () => {
      component.lastPage();
      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('2');
    });

    it('retry 應重新載入目前頁', () => {
      component.page = 2;
      component.retry();
      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('2');
    });
  });

  describe('getPageNumbers', () => {
    it('總頁數為 0 應回傳空陣列', () => {
      component.totalPages = 0;
      expect(component.getPageNumbers()).toEqual([]);
    });

    it('總頁數小於等於 7 應列出所有頁碼', () => {
      component.totalPages = 7;
      expect(component.getPageNumbers()).toEqual([1, 2, 3, 4, 5, 6, 7]);
    });

    it('總頁數多於 7 且目前頁在開頭應插入省略號', () => {
      component.totalPages = 10;
      component.page = 0;
      expect(component.getPageNumbers()).toEqual([1, 2, '...', 9, 10]);
    });

    it('目前頁在中段應保留前後頁並插入省略號', () => {
      component.totalPages = 10;
      component.page = 4;
      expect(component.getPageNumbers()).toEqual([1, 2, '...', 4, 5, 6, '...', 9, 10]);
    });

    it('onPageNumberClick 收到數字應轉為 0-based 頁碼', () => {
      component.totalPages = 5;
      component.onPageNumberClick(3);
      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('2');
    });

    it('onPageNumberClick 收到省略號應忽略', () => {
      component.totalPages = 10;
      component.onPageNumberClick('...');
      expect(api.getPage).not.toHaveBeenCalled();
    });
  });

  describe('模板渲染', () => {
    it('應顯示 config 標題與 placeholder', () => {
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('h1').textContent).toContain('天然油品');
      expect(fixture.nativeElement.querySelector('input').getAttribute('placeholder'))
        .toBe('輸入關鍵字搜尋油品名稱');
    });

    it('搜尋輸入框應有 maxlength=100（與後端 KeywordSanitizer.MAX_LENGTH 對齊）', () => {
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('input').getAttribute('maxlength')).toBe('100');
    });

    it('無關鍵字且無結果時應顯示 emptyWithoutKeyword', () => {
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('查無油品資料');
    });

    it('有關鍵字且無結果時應顯示 emptyWithKeyword', () => {
      component.searchKeyword = '不存在的油';
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('查無符合條件的油品');
    });

    it('應依 detailField 顯示對應欄位（benefits）', () => {
      api.getPage.and.returnValue(of(makePage([makeOil()])));
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('深層滋潤');
    });

    it('detailField 切換為 scentNotes 應顯示香調欄位', () => {
      api.getPage.and.returnValue(of(makePage([makeEssentialOil()])));
      component.config = { ...oilConfig, detailField: 'scentNotes' };
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('舒緩放鬆');
    });

    it('連結應使用 config.routePrefix', () => {
      api.getPage.and.returnValue(of(makePage([makeOil()])));
      fixture.detectChanges();
      const link = fixture.debugElement.query(By.css('a')).nativeElement as HTMLAnchorElement;
      expect(link.getAttribute('href')).toContain('/oils/1');
    });
  });
});
