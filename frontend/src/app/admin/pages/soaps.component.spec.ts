import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ElementRef } from '@angular/core';
import { of, throwError } from 'rxjs';
import { SoapsComponent } from './soaps.component';
import { ApiService } from '../../shared/services/api.service';
import {
  fakeFile,
  fakeFileEvent,
  makeCategory,
  makeEssentialOil,
  makeOil,
  makePage,
  makeSoap,
  makeSoapOilRef,
  mockApiService,
  spyComputedStyle,
  stubElementRef,
  stubFileReader
} from '../../testing/test-helpers';
import { MAX_UPLOAD_SIZE, UPLOAD_SIZE_ERROR_MSG } from '../../shared/constants';

describe('SoapsComponent (admin)', () => {
  let fixture: ComponentFixture<SoapsComponent>;
  let component: SoapsComponent;
  let api: jasmine.SpyObj<ApiService>;

  const allOils = () => [
    makeOil({ id: 1, name: '椰子油', origin: '菲律賓' }),
    makeOil({ id: 2, name: '橄欖油', origin: '西班牙' }),
    makeOil({ id: 3, name: '甜杏仁油', origin: '美國' })
  ];

  const allEssentialOils = () => [
    makeEssentialOil({ id: 1, name: '薰衣草精油', scentNotes: 'Cedarwood 雪松。放鬆' }),
    makeEssentialOil({ id: 2, name: '茶樹精油', scentNotes: '淨化抗菌' })
  ];

  beforeEach(async () => {
    api = mockApiService();
    api.get.and.returnValue(of([makeCategory()]));
    api.getPage.and.returnValue(of(makePage([makeSoap()], { totalPages: 2, number: 0 })));

    await TestBed.configureTestingModule({
      imports: [SoapsComponent, FormsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: ElementRef, useValue: stubElementRef('auto') }
      ]
    }).compileComponents();

    spyComputedStyle('auto');

    fixture = TestBed.createComponent(SoapsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => fixture.destroy());

  describe('初始化', () => {
    it('ngOnInit 應載入分類、油品、精油與手工皂列表', () => {
      component.ngOnInit();

      // 列表本身走 getPage（分頁），油品／精油選單走 get（一次抓全部）
      expect(api.get).toHaveBeenCalledWith('categories');
      expect(api.getPage.calls.allArgs().map(args => args[0])).toContain('admin/soaps');
    });

    it('油品與精油選單應一次載入大量資料供下拉多選', () => {
      component.ngOnInit();

      // 選單用 get（回傳陣列）一次抓全部，而非分頁的 getPage
      const endpoints = api.get.calls.allArgs().map(args => args[0]);
      expect(endpoints).toContain('admin/oils');
      expect(endpoints).toContain('admin/essential-oils');
    });

    it('油品載入失敗時應設為空陣列', () => {
      api.get.and.returnValue(throwError(() => new Error('network')));

      component.ngOnInit();

      expect(component.allOils).toEqual([]);
      expect(component.allEssentialOils).toEqual([]);
    });

    it('列表載入失敗時應清空列表與分頁狀態', () => {
      spyOn(console, 'error');
      api.get.and.returnValue(of([]));
      api.getPage.and.returnValue(throwError(() => new Error('network')));
      component.soaps = [makeSoap()];
      component.totalElements = 50;

      component.ngOnInit();

      expect(component.soaps).toEqual([]);
      expect(component.totalElements).toBe(0);
      expect(component.totalPages).toBe(0);
    });
  });
  describe('油品 / 精油多選', () => {
    beforeEach(() => {
      component.allOils = allOils();
      component.allEssentialOils = allEssentialOils();
    });

    it('toggleOil 應新增選取項目', () => {
      component.toggleOil(1);

      expect(component.selectedOilIds).toEqual([1]);
      expect(component.isOilSelected(1)).toBeTrue();
    });

    it('toggleOil 再次點擊應移除選取', () => {
      component.toggleOil(1);
      component.toggleOil(1);

      expect(component.selectedOilIds).toEqual([]);
      expect(component.isOilSelected(1)).toBeFalse();
    });

    it('isOilSelected 對未選項目應回傳 false', () => {
      expect(component.isOilSelected(99)).toBeFalse();
    });

    it('getSelectedOils 應回傳對應的油品物件', () => {
      component.toggleOil(1);
      component.toggleOil(3);

      const selected = component.getSelectedOils();

      expect(selected.map(o => o.name)).toEqual(['椰子油', '甜杏仁油']);
    });

    it('getSelectedOils 應忽略找不到的 id', () => {
      component.selectedOilIds = [1, 999];

      expect(component.getSelectedOils().length).toBe(1);
    });

    it('toggleEssentialOil 應新增與移除精油選取', () => {
      component.toggleEssentialOil(1);
      expect(component.isEssentialOilSelected(1)).toBeTrue();

      component.toggleEssentialOil(1);
      expect(component.isEssentialOilSelected(1)).toBeFalse();
    });

    it('getSelectedEssentialOils 應回傳對應的精油物件', () => {
      component.toggleEssentialOil(2);

      expect(component.getSelectedEssentialOils().map(e => e.name)).toEqual(['茶樹精油']);
    });
  });

  describe('油品 / 精油關鍵字篩選', () => {
    beforeEach(() => {
      component.allOils = allOils();
      component.allEssentialOils = allEssentialOils();
    });

    it('無關鍵字時應回傳全部油品', () => {
      component.oilKeyword = '';

      expect(component.getFilteredOils().length).toBe(3);
    });

    it('應以名稱篩選油品且不分大小寫', () => {
      component.oilKeyword = '椰';

      expect(component.getFilteredOils().map(o => o.name)).toEqual(['椰子油']);
    });

    it('應以產地篩選油品', () => {
      component.oilKeyword = '西班牙';

      expect(component.getFilteredOils().map(o => o.name)).toEqual(['橄欖油']);
    });

    it('無符合結果時應回傳空陣列', () => {
      component.oilKeyword = '不存在的油';

      expect(component.getFilteredOils()).toEqual([]);
    });

    it('應以精油名稱篩選', () => {
      component.essentialOilKeyword = '茶樹';

      expect(component.getFilteredEssentialOils().map(e => e.name)).toEqual(['茶樹精油']);
    });

    it('應以 scentNotes 前段篩選精油', () => {
      component.essentialOilKeyword = 'Cedarwood';

      expect(component.getFilteredEssentialOils().map(e => e.name)).toEqual(['薰衣草精油']);
    });

    it('關鍵字只有空白時應視為無篩選', () => {
      component.oilKeyword = '   ';
      component.essentialOilKeyword = '   ';

      expect(component.getFilteredOils().length).toBe(3);
      expect(component.getFilteredEssentialOils().length).toBe(2);
    });
  });
  describe('成分彙整', () => {
    it('無任何成分時應顯示破折號', () => {
      const soap = makeSoap({ oils: [], essentialOils: [], ingredients: '' });

      expect(component.getIngredientSummary(soap)).toBe('-');
    });

    it('應合併油品、精油與其他成分並以「、」串接', () => {
      const soap = makeSoap({
        oils: [makeSoapOilRef(1, '椰子油')],
        essentialOils: [makeSoapOilRef(1, '薰衣草精油')],
        ingredients: '氫氧化鈉'
      });

      expect(component.getIngredientSummary(soap)).toBe('椰子油、薰衣草精油、氫氧化鈉');
    });

    it('oils 為 undefined 時不應拋出例外', () => {
      const soap = makeSoap({ oils: undefined, essentialOils: undefined });

      expect(component.getIngredientSummary(soap)).toBe('-');
    });
  });

  describe('新增 / 編輯 Modal', () => {
    it('openModal 應重設所有欄位為新增預設值', () => {
      component.isEdit = true;
      component.selectedCategoryId = 5;
      component.selectedOilIds = [1, 2];
      component.oilKeyword = '舊值';
      component.selectedFile = fakeFile();
      component.fileError = 'err';
      component.saveError = 'err';

      component.openModal();

      expect(component.showModal).toBeTrue();
      expect(component.isEdit).toBeFalse();
      expect(component.currentSoap.isFeatured).toBeFalse();
      expect(component.selectedCategoryId).toBe(0);
      expect(component.selectedOilIds).toEqual([]);
      expect(component.selectedEssentialOilIds).toEqual([]);
      expect(component.oilKeyword).toBe('');
      expect(component.selectedFile).toBeNull();
      expect(component.fileError).toBeNull();
      expect(component.saveError).toBeNull();
    });

    it('editSoap 應回填分類與油品精油選取狀態', () => {
      const soap = makeSoap({
        id: 4,
        category: { id: 2, name: '清潔型' },
        oils: [makeSoapOilRef(1, '椰子油'), makeSoapOilRef(2, '橄欖油')],
        essentialOils: [makeSoapOilRef(1, '茶樹精油')]
      });

      component.editSoap(soap);

      expect(component.showModal).toBeTrue();
      expect(component.isEdit).toBeTrue();
      expect(component.selectedCategoryId).toBe(2);
      expect(component.selectedOilIds).toEqual([1, 2]);
      expect(component.selectedEssentialOilIds).toEqual([1]);
    });

    it('editSoap 對無分類的商品應將 selectedCategoryId 設為 0', () => {
      component.editSoap(makeSoap({ category: null }));

      expect(component.selectedCategoryId).toBe(0);
    });

    it('closeModal 應關閉 Modal', () => {
      component.showModal = true;

      component.closeModal();

      expect(component.showModal).toBeFalse();
    });
  });
  describe('儲存', () => {
    beforeEach(() => {
      api.get.and.returnValue(of([makeCategory()]));
    });

    it('新增模式應以 POST 送出並帶上分類與油品精油 id', () => {
      api.post.and.returnValue(of(makeSoap({ id: 11 })));
      component.currentSoap = { name: '新手工皂' };
      component.selectedCategoryId = 2;
      component.selectedOilIds = [1, 3];
      component.selectedEssentialOilIds = [1];
      component.isEdit = false;

      component.saveSoap();

      const [url, payload] = api.post.calls.mostRecent().args as [string, any];
      expect(url).toBe('admin/soaps');
      expect(payload.categoryId).toBe(2);
      expect(payload.oilIds).toEqual([1, 3]);
      expect(payload.essentialOilIds).toEqual([1]);
      expect(component.showModal).toBeFalse();
    });

    it('編輯模式且有 id 時應以 PUT 送出', () => {
      api.put.and.returnValue(of(makeSoap()));
      component.currentSoap = { id: 7, name: '改名' };
      component.isEdit = true;

      component.saveSoap();

      expect(api.put.calls.mostRecent().args[0]).toBe('admin/soaps/7');
      expect(api.post).not.toHaveBeenCalled();
    });

    it('儲存失敗時應設定 saveError 並保持 Modal 開啟', () => {
      api.post.and.returnValue(throwError(() => ({ error: { message: '分類不存在' } })));
      component.isEdit = false;
      component.showModal = true;

      component.saveSoap();

      expect(component.saveError).toBe('分類不存在');
      expect(component.showModal).toBeTrue();
    });
  });

  describe('圖片上傳', () => {
    it('檔案超過 20MB 時應擋下並清空 input', () => {
      const event = fakeFileEvent(fakeFile('big.png', MAX_UPLOAD_SIZE + 1));

      component.onFileSelected(event);

      expect(component.fileError).toBe(UPLOAD_SIZE_ERROR_MSG);
      expect(component.selectedFile).toBeNull();
      expect(event.target.value).toBe('');
    });

    it('合法檔案應觸發 FileReader 產生預覽', () => {
      const { reader, complete } = stubFileReader();
      spyOn(window as any, 'FileReader').and.returnValue(reader);

      component.onFileSelected(fakeFileEvent(fakeFile()));
      complete('preview');

      expect(reader.readAsDataURL).toHaveBeenCalled();
      expect(component.selectedImagePreview).toBe('preview');
    });

    it('新增成功且有選檔時應以新 id 上傳圖片', () => {
      api.get.and.returnValue(of([]));
      api.post.and.returnValue(of(makeSoap({ id: 11 })));
      component.selectedFile = fakeFile();
      component.isEdit = false;

      component.saveSoap();

      expect(api.upload).toHaveBeenCalledWith('admin/soaps/11/images', component.selectedFile, {
        sortOrder: '0',
        isPrimary: 'true'
      });
    });

    it('未選檔時不應呼叫上傳', () => {
      api.get.and.returnValue(of([]));
      api.post.and.returnValue(of(makeSoap({ id: 11 })));
      component.selectedFile = null;
      component.isEdit = false;

      component.saveSoap();

      expect(api.upload).not.toHaveBeenCalled();
    });
  });

  describe('刪除', () => {
    beforeEach(() => {
      api.get.and.returnValue(of([]));
      api.delete.and.returnValue(of(null));
      component.ngOnInit();
      api.getPage.calls.reset();
    });

    it('確認後應送出刪除並重新載入', () => {
      spyOn(window, 'confirm').and.returnValue(true);

      component.deleteSoap(3);

      expect(api.delete).toHaveBeenCalledWith('admin/soaps/3');
      expect(api.getPage).toHaveBeenCalled();
    });

    it('取消確認時不應送出刪除', () => {
      spyOn(window, 'confirm').and.returnValue(false);

      component.deleteSoap(3);

      expect(api.delete).not.toHaveBeenCalled();
    });

    it('刪除最後一筆且非首頁時應自動回到上一頁', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      component.soaps = [makeSoap()];
      component.page = 2;

      component.deleteSoap(3);

      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('1');
    });
  });

  describe('搜尋防抖', () => {
    beforeEach(() => {
      component.ngOnInit();
      api.getPage.calls.reset();
    });

    it('輸入後 300ms 內不應立即查詢', fakeAsync(() => {
      component.onSearchInput();
      tick(299);

      expect(api.getPage).not.toHaveBeenCalled();
      // 清掉尚未觸發的 timer，避免殘留到後續測試
      flush();
    }));

    it('輸入滿 300ms 後應以第 0 頁查詢', fakeAsync(() => {
      component.searchKeyword = '皂';
      component.onSearchInput();
      tick(300);

      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('0');
    }));

    it('clearSearch 應清空關鍵字並立即重新查詢', () => {
      component.searchKeyword = '舊值';

      component.clearSearch();

      expect(component.searchKeyword).toBe('');
      expect(api.getPage.calls.mostRecent().args[1]?.has('keyword')).toBeFalse();
    });

    it('ngOnDestroy 應清除待觸發的搜尋 timer', fakeAsync(() => {
      component.onSearchInput();
      api.getPage.calls.reset();

      component.ngOnDestroy();
      tick(300);

      expect(api.getPage).not.toHaveBeenCalled();
    }));
  });

  describe('捲動監聽', () => {
    /**
     * ElementRef 由框架自動注入指向真實 host element，findScrollContainer()
     * 會沿真實 DOM 往上找而找不到捲動容器，因此這裡直接指定可控的容器物件。
     */
    const attachContainer = () => {
      const container: any = {
        scrollTop: 0,
        scrollTo: jasmine.createSpy('scrollTo'),
        addEventListener: jasmine.createSpy('addEventListener'),
        removeEventListener: jasmine.createSpy('removeEventListener')
      };
      (component as any).scrollContainer = container;
      return container;
    };

    it('捲動超過 300px 時應顯示回到頂端按鈕', () => {
      const container = attachContainer();
      container.scrollTop = 500;

      component.onScroll();

      expect(component.showScrollTop).toBeTrue();
    });

    it('捲動小於 300px 時應隱藏回到頂端按鈕', () => {
      const container = attachContainer();
      container.scrollTop = 50;

      component.onScroll();

      expect(component.showScrollTop).toBeFalse();
    });

    it('ngOnDestroy 應移除捲動監聽', () => {
      const container = attachContainer();

      component.ngOnDestroy();

      expect(container.removeEventListener).toHaveBeenCalled();
    });
  });

  it('應渲染手工皂列表', () => {
    api.getPage.and.returnValue(of(makePage([makeSoap({ name: '燕麥溫和皂' })])));

    component.ngOnInit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('燕麥溫和皂');
  });
});