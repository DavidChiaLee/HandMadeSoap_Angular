import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SoapsComponent } from './soaps.component';
import { ApiService } from '../../shared/services/api.service';
import { makeCategory, makePage, makeSoap, mockApiService } from '../../testing/test-helpers';

describe('SoapsComponent', () => {
  let fixture: ComponentFixture<SoapsComponent>;
  let component: SoapsComponent;
  let api: jasmine.SpyObj<ApiService>;

  const makeSoaps = () => [
    makeSoap({
      id: 1,
      name: '茶樹淨膚皂',
      price: 150,
      category: { id: 2, name: '清潔型' }
    }),
    makeSoap({
      id: 2,
      name: '玫瑰保濕皂',
      price: 200,
      category: { id: 1, name: '滋潤型' }
    })
  ];

  beforeEach(async () => {
    api = mockApiService();
    await TestBed.configureTestingModule({
      imports: [SoapsComponent],
      providers: [provideRouter([]), { provide: ApiService, useValue: api }]
    }).compileComponents();

    fixture = TestBed.createComponent(SoapsComponent);
    component = fixture.componentInstance;
  });

  describe('初次載入', () => {
    it('應同時載入分類與手工皂列表', () => {
      api.getList.and.returnValue(of([makeCategory()]));
      api.getPage.and.returnValue(of(makePage(makeSoaps())));

      component.ngOnInit();

      expect(api.getList).toHaveBeenCalledWith('categories');
      expect(api.getPage).toHaveBeenCalled();
    });

    it('分類載入失敗時應顯示錯誤並可重試，而非餵假分類', () => {
      api.getList.and.returnValue(throwError(() => new Error('network')));
      api.getPage.and.returnValue(of(makePage([])));

      component.ngOnInit();

      expect(component.categories).toEqual([]);
      expect(component.categoryError).toBeTruthy();
    });

    it('應以 page/size/sort 參數載入手工皂', () => {
      api.getList.and.returnValue(of([]));
      api.getPage.and.returnValue(of(makePage([])));

      component.ngOnInit();

      const params = api.getPage.calls.mostRecent().args[1];
      expect(params?.get('page')).toBe('0');
      expect(params?.get('size')).toBe('8');
    });
  });

  describe('分類篩選', () => {
    beforeEach(() => {
      api.getList.and.returnValue(of([makeCategory()]));
      api.getPage.and.returnValue(of(makePage(makeSoaps(), { totalPages: 1, number: 0 })));
      component.ngOnInit();
      api.getPage.calls.reset();
    });

    it('選擇分類後應以該分類重新載入並記錄選取狀態', () => {
      component.filterByCategory(2);

      expect(component.selectedCategory).toBe(2);
      expect(api.getPage).toHaveBeenCalled();
    });

    it('以 null 篩選（全部）時應清除選取狀態', () => {
      component.selectedCategory = 2;

      component.filterByCategory(null);

      expect(component.selectedCategory).toBeNull();
    });

    it('切換分類時應回到第 0 頁', () => {
      component.page = 3;

      component.filterByCategory(1);

      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('0');
    });
  });

  describe('排序', () => {
    beforeEach(() => {
      api.getList.and.returnValue(of([]));
      api.getPage.and.returnValue(of(makePage(makeSoaps(), { totalPages: 1, number: 0 })));
      component.ngOnInit();
      api.getPage.calls.reset();
    });

    it('sortProducts 應以 sort 參數重新載入', () => {
      component.sortBy = 'price-low';

      component.sortProducts();

      expect(api.getPage.calls.mostRecent().args[1]?.get('sort')).toContain('price');
    });

    it('依價格低至高排序時應送出 price,asc', () => {
      component.sortBy = 'price-low';

      component.sortProducts();

      expect(api.getPage.calls.mostRecent().args[1]?.get('sort')).toBe('price,asc');
    });

    it('依價格高至低排序時應送出 price,desc', () => {
      component.sortBy = 'price-high';

      component.sortProducts();

      expect(api.getPage.calls.mostRecent().args[1]?.get('sort')).toBe('price,desc');
    });

    it('依名稱排序時應送出 name,asc', () => {
      component.sortBy = 'name';

      component.sortProducts();

      expect(api.getPage.calls.mostRecent().args[1]?.get('sort')).toBe('name,asc');
    });
  });

  describe('分頁', () => {
    beforeEach(() => {
      api.getList.and.returnValue(of([]));
      api.getPage.and.returnValue(of(makePage(makeSoaps(), { totalPages: 4, number: 0 })));
      component.ngOnInit();
      api.getPage.calls.reset();
    });

    it('goToPage 對負數、超界與同頁應忽略', () => {
      component.goToPage(-1);
      component.goToPage(10);
      component.goToPage(0);

      expect(api.getPage).not.toHaveBeenCalled();
    });

    it('nextPage 與 lastPage 應前往對應頁次', () => {
      component.nextPage();
      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('1');

      component.lastPage();
      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('3');
    });

    it('prevPage 與 firstPage 應前往前頁與首頁', () => {
      component.page = 2;
      component.prevPage();
      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('1');

      component.page = 3;
      component.firstPage();
      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('0');
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
      component.totalPages = 4;

      expect(component.getPageNumbers()).toEqual([1, 2, 3, 4]);
    });

    it('總頁數多於 7 時應插入省略號', () => {
      component.totalPages = 20;
      component.page = 10;

      expect(component.getPageNumbers()).toContain('...');
    });
  });

  describe('載入失敗', () => {
    it('列表請求失敗時應顯示錯誤並可重試，而非餵假資料', () => {
      api.getList.and.returnValue(of([]));
      api.getPage.and.returnValue(throwError(() => new Error('network')));

      component.ngOnInit();

      expect(component.soaps).toEqual([]);
      expect(component.totalPages).toBe(0);
      expect(component.errorMessage).toBeTruthy();
    });

    it('重試應以目前頁碼重新請求', () => {
      api.getList.and.returnValue(of([]));
      api.getPage.and.returnValue(throwError(() => new Error('network')));
      component.ngOnInit();
      api.getPage.calls.reset();
      api.getPage.and.returnValue(of(makePage([])));

      component.retry();

      expect(api.getPage).toHaveBeenCalled();
    });

    it('選擇分類後應以 categoryId 查詢參數打統一 soaps 端點', () => {
      api.getList.and.returnValue(of([]));
      api.getPage.and.returnValue(of(makePage([])));
      component.ngOnInit();
      api.getPage.calls.reset();

      component.filterByCategory(2);

      const args = api.getPage.calls.mostRecent().args;
      expect(args[0]).toBe('soaps');
      expect(args[1]?.get('categoryId')).toBe('2');
      expect(args[1]?.get('page')).toBe('0');
    });
  });

  it('應渲染手工皂卡片', () => {
    api.getList.and.returnValue(of([makeCategory()]));
    api.getPage.and.returnValue(of(makePage([makeSoap({ name: '燕麥溫和皂' })])));

    component.ngOnInit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('燕麥溫和皂');
  });
});
