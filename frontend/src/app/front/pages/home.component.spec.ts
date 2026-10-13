import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HomeComponent } from './home.component';
import { ApiService } from '../../shared/services/api.service';
import {
  makeCategory,
  makePage,
  makeSoap,
  mockApiService
} from '../../testing/test-helpers';

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  let component: HomeComponent;
  let api: jasmine.SpyObj<ApiService>;

  beforeEach(async () => {
    api = mockApiService();
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter([]),
        { provide: ApiService, useValue: api }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
  });

  describe('精選商品載入', () => {
    it('精選商品有資料時應直接採用，不另外請求最新商品', () => {
      const featured = [makeSoap({ id: 1 }), makeSoap({ id: 2 })];
      api.getPage.and.returnValue(of(makePage(featured)));
      api.getList.and.returnValue(of([]));

      component.ngOnInit();

      expect(component.featuredSoaps).toEqual(featured);
      expect(api.getPage).toHaveBeenCalledTimes(1);
      expect(api.getPage.calls.mostRecent().args[0]).toBe('soaps/featured');
    });

    it('精選商品為空時應改載入最新上架商品', () => {
      const latest = [makeSoap({ id: 9 })];
      api.getPage.and.returnValue(of(makePage([])));
      api.getList.and.returnValue(of([]));

      component.ngOnInit();
      // 第二個 getPage 為最新商品請求
      api.getPage.and.returnValue(of(makePage(latest)));
      (component as any).loadLatestSoaps();

      expect(api.getPage.calls.mostRecent().args[0]).toBe('soaps');
      expect(component.featuredSoaps).toEqual(latest);
    });

    it('精選商品請求失敗時應改載入最新上架商品', () => {
      api.getPage.and.returnValue(throwError(() => new Error('network')));
      api.getList.and.returnValue(of([]));

      component.ngOnInit();

      expect(api.getPage.calls.mostRecent().args[0]).toBe('soaps');
    });

    it('精選與最新商品都失敗時應顯示錯誤並可重試，而非餵假資料', () => {
      api.getPage.and.returnValue(throwError(() => new Error('network')));
      api.getList.and.returnValue(of([]));

      component.ngOnInit();

      expect(component.featuredSoaps).toEqual([]);
      expect(component.featuredError).toBeTruthy();
    });

    it('精選與最新商品都失敗時應顯示錯誤並可重試，而非餵假資料', () => {
      api.getPage.and.returnValue(throwError(() => new Error('network')));
      api.getList.and.returnValue(of([]));

      component.ngOnInit();

      expect(component.featuredSoaps).toEqual([]);
      expect(component.featuredError).toBeTruthy();
    });

    it('請求精選商品時應帶入 page=0 與 size=6', () => {
      api.getPage.and.returnValue(of(makePage([])));
      api.getList.and.returnValue(of([]));

      component.ngOnInit();

      const params = api.getPage.calls.mostRecent().args[1];
      expect(params?.get('page')).toBe('0');
      expect(params?.get('size')).toBe('6');
    });
  });

  describe('分類載入', () => {
    it('應過濾掉已停用的分類', () => {
      api.getPage.and.returnValue(of(makePage([])));
      api.getList.and.returnValue(of([
        makeCategory({ id: 1, name: '一般家事皂', isActive: true }),
        makeCategory({ id: 2, name: '已下架', isActive: false })
      ]));

      component.ngOnInit();

      expect(component.categories.length).toBe(1);
      expect(component.categories[0].name).toBe('一般家事皂');
    });

    it('應依 sortOrder 由小到大排序', () => {
      api.getPage.and.returnValue(of(makePage([])));
      api.getList.and.returnValue(of([
        makeCategory({ id: 1, name: 'C', sortOrder: 3 }),
        makeCategory({ id: 2, name: 'A', sortOrder: 1 }),
        makeCategory({ id: 3, name: 'B', sortOrder: 2 })
      ]));

      component.ngOnInit();

      expect(component.categories.map(c => c.name)).toEqual(['A', 'B', 'C']);
    });

    it('sortOrder 為 null 或 undefined 時應視為 0 排在最前', () => {
      api.getPage.and.returnValue(of(makePage([])));
      api.getList.and.returnValue(of([
        makeCategory({ id: 1, name: '有排序', sortOrder: 5 }),
        { ...makeCategory({ id: 2, name: '無排序' }), sortOrder: undefined as any }
      ]));

      component.ngOnInit();

      expect(component.categories[0].name).toBe('無排序');
    });

    it('分類請求失敗時應顯示錯誤並可重試，而非餵假分類', () => {
      api.getPage.and.returnValue(of(makePage([])));
      api.getList.and.returnValue(throwError(() => new Error('network')));

      component.ngOnInit();

      expect(component.categories).toEqual([]);
      expect(component.categoryError).toBeTruthy();
    });

    it('應向 categories 端點請求分類', () => {
      api.getPage.and.returnValue(of(makePage([])));
      api.getList.and.returnValue(of([]));

      component.ngOnInit();

      expect(api.getList).toHaveBeenCalledWith('categories');
    });
  });

  it('應渲染精選商品區塊', () => {
    api.getPage.and.returnValue(of(makePage([makeSoap({ id: 1, name: '玫瑰保濕皂' })])));
    api.getList.and.returnValue(of([]));

    component.ngOnInit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('玫瑰保濕皂');
  });
});
