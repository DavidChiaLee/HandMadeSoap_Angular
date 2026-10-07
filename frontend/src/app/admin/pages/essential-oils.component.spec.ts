import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ElementRef } from '@angular/core';
import { of, throwError } from 'rxjs';
import { EssentialOilsComponent } from './essential-oils.component';
import { ApiService } from '../../shared/services/api.service';
import {
  fakeFile,
  fakeFileEvent,
  makeEssentialOil,
  makePage,
  mockApiService,
  spyComputedStyle,
  stubElementRef,
  stubFileReader
} from '../../testing/test-helpers';
import { MAX_UPLOAD_SIZE, UPLOAD_SIZE_ERROR_MSG } from '../../shared/constants';

describe('EssentialOilsComponent (admin)', () => {
  let fixture: ComponentFixture<EssentialOilsComponent>;
  let component: EssentialOilsComponent;
  let api: jasmine.SpyObj<ApiService>;

  beforeEach(async () => {
    api = mockApiService();
    api.getPage.and.returnValue(
      of(makePage([makeEssentialOil()], { totalPages: 3, number: 0 }))
    );

    await TestBed.configureTestingModule({
      imports: [EssentialOilsComponent, FormsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: ElementRef, useValue: stubElementRef('auto') }
      ]
    }).compileComponents();

    spyComputedStyle('auto');

    fixture = TestBed.createComponent(EssentialOilsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => fixture.destroy());

  describe('載入列表', () => {
    it('ngOnInit 應以 page/size/sort 載入 admin/essential-oils', () => {
      component.ngOnInit();

      const params = api.getPage.calls.mostRecent().args[1];
      expect(params?.get('page')).toBe('0');
      expect(params?.get('size')).toBe('5');
      expect(params?.get('sort')).toBe('id,desc');
    });

    it('有搜尋關鍵字時應附加 keyword 參數', () => {
      component.searchKeyword = '薰衣草';

      component.loadEssentialOils();

      expect(api.getPage.calls.mostRecent().args[1]?.get('keyword')).toBe('薰衣草');
    });

    it('關鍵字只有空白時不應附加 keyword 參數', () => {
      component.searchKeyword = '  ';

      component.loadEssentialOils();

      expect(api.getPage.calls.mostRecent().args[1]?.has('keyword')).toBeFalse();
    });

    it('載入成功後應寫入內容與分頁狀態', () => {
      api.getPage.and.returnValue(
        of(makePage([makeEssentialOil({ id: 1 })], { number: 2, totalPages: 5, totalElements: 30 }))
      );

      component.ngOnInit();

      expect(component.essentialOils.length).toBe(1);
      expect(component.page).toBe(2);
      expect(component.totalPages).toBe(5);
      expect(component.totalElements).toBe(30);
    });

    it('載入失敗時應清空列表與分頁狀態', () => {
      spyOn(console, 'error');
      api.getPage.and.returnValue(throwError(() => new Error('network')));
      component.essentialOils = [makeEssentialOil()];
      component.totalElements = 99;

      component.ngOnInit();

      expect(component.essentialOils).toEqual([]);
      expect(component.totalElements).toBe(0);
      expect(component.totalPages).toBe(0);
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
      component.searchKeyword = '茶';
      component.onSearchInput();
      tick(300);

      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('0');
    }));

    it('連續輸入應只觸發最後一次查詢', fakeAsync(() => {
      component.onSearchInput();
      tick(200);
      component.onSearchInput();
      tick(200);
      component.onSearchInput();
      tick(300);

      expect(api.getPage).toHaveBeenCalledTimes(1);
    }));

    it('clearSearch 應清空關鍵字並立即重新查詢', () => {
      component.searchKeyword = '舊值';

      component.clearSearch();

      expect(component.searchKeyword).toBe('');
      expect(api.getPage.calls.mostRecent().args[1]?.has('keyword')).toBeFalse();
    });
  });
  describe('新增 / 編輯 Modal', () => {
    it('openModal 應清空所有欄位並設為新增模式', () => {
      component.isEdit = true;
      component.currentEssentialOil = { id: 5, name: '舊值' };
      component.selectedFile = fakeFile();
      component.fileError = 'err';
      component.saveError = 'err';

      component.openModal();

      expect(component.showModal).toBeTrue();
      expect(component.isEdit).toBeFalse();
      expect(component.currentEssentialOil).toEqual({});
      expect(component.selectedFile).toBeNull();
      expect(component.fileError).toBeNull();
      expect(component.saveError).toBeNull();
    });

    it('editEssentialOil 應帶入資料並設為編輯模式', () => {
      const oil = makeEssentialOil({ id: 3, name: '茶樹精油' });

      component.editEssentialOil(oil);

      expect(component.showModal).toBeTrue();
      expect(component.isEdit).toBeTrue();
      expect(component.currentEssentialOil.name).toBe('茶樹精油');
    });

    it('closeModal 應關閉 Modal', () => {
      component.showModal = true;

      component.closeModal();

      expect(component.showModal).toBeFalse();
    });
  });

  describe('儲存', () => {
    it('新增模式應以 POST 送出', () => {
      api.post.and.returnValue(of(makeEssentialOil({ id: 9 })));
      component.currentEssentialOil = { name: '新品' };
      component.isEdit = false;

      component.saveEssentialOil();

      expect(api.post).toHaveBeenCalledWith('admin/essential-oils', { name: '新品' });
      expect(component.showModal).toBeFalse();
    });

    it('編輯模式且有 id 時應以 PUT 送出', () => {
      api.put.and.returnValue(of(makeEssentialOil()));
      component.currentEssentialOil = { id: 7, name: '改名' };
      component.isEdit = true;

      component.saveEssentialOil();

      expect(api.put).toHaveBeenCalledWith('admin/essential-oils/7', { id: 7, name: '改名' });
      expect(api.post).not.toHaveBeenCalled();
    });

    it('儲存失敗時應設定 saveError 並保持 Modal 開啟', () => {
      api.post.and.returnValue(throwError(() => ({ error: { message: '名稱重複' } })));
      component.isEdit = false;
      component.showModal = true;

      component.saveEssentialOil();

      expect(component.saveError).toBe('名稱重複');
      expect(component.showModal).toBeTrue();
    });
  });
  describe('圖片上傳', () => {
    it('檔案超過 20MB 時應擋下並顯示錯誤', () => {
      const event = fakeFileEvent(fakeFile('big.png', MAX_UPLOAD_SIZE + 1));

      component.onFileSelected(event);

      expect(component.fileError).toBe(UPLOAD_SIZE_ERROR_MSG);
      expect(component.selectedFile).toBeNull();
      expect(component.selectedImagePreview).toBeNull();
      expect(event.target.value).toBe('');
    });

    it('檔案大小剛好等於上限時應允許', () => {
      const file = fakeFile('ok.png', MAX_UPLOAD_SIZE);
      const { reader } = stubFileReader();
      spyOn(window as any, 'FileReader').and.returnValue(reader);

      component.onFileSelected(fakeFileEvent(file));

      expect(component.fileError).toBeNull();
      expect(component.selectedFile).toBe(file);
      expect(reader.readAsDataURL).toHaveBeenCalled();
    });

    it('合法檔案應觸發 FileReader 產生預覽', () => {
      const { reader, complete } = stubFileReader();
      spyOn(window as any, 'FileReader').and.returnValue(reader);

      component.onFileSelected(fakeFileEvent(fakeFile()));
      complete('data:image/png;base64,BBB');

      expect(reader.readAsDataURL).toHaveBeenCalled();
      expect(component.selectedImagePreview).toBe('data:image/png;base64,BBB');
    });

    it('儲存後有選檔時應呼叫上傳端點', () => {
      api.post.and.returnValue(of(makeEssentialOil({ id: 9 })));
      component.selectedFile = fakeFile();
      component.isEdit = false;

      component.saveEssentialOil();

      expect(api.upload).toHaveBeenCalledWith(
        'admin/essential-oils/9/images',
        component.selectedFile,
        { sortOrder: '0', isPrimary: 'true' }
      );
    });

    it('儲存後未選檔時不應呼叫上傳', () => {
      api.post.and.returnValue(of(makeEssentialOil({ id: 9 })));
      component.selectedFile = null;
      component.isEdit = false;

      component.saveEssentialOil();

      expect(api.upload).not.toHaveBeenCalled();
    });
  });
  describe('刪除', () => {
    beforeEach(() => {
      api.delete.and.returnValue(of(null));
      component.ngOnInit();
      api.getPage.calls.reset();
    });

    it('確認後應送出刪除並重新載入', () => {
      spyOn(window, 'confirm').and.returnValue(true);

      component.deleteEssentialOil(3);

      expect(api.delete).toHaveBeenCalledWith('admin/essential-oils/3');
      expect(api.getPage).toHaveBeenCalled();
    });

    it('取消確認時不應送出刪除', () => {
      spyOn(window, 'confirm').and.returnValue(false);

      component.deleteEssentialOil(3);

      expect(api.delete).not.toHaveBeenCalled();
    });

    it('刪除最後一筆且非首頁時應自動回到上一頁', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      component.essentialOils = [makeEssentialOil()];
      component.page = 2;

      component.deleteEssentialOil(3);

      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('1');
    });
  });

  describe('單張圖片刪除', () => {
    beforeEach(() => {
      component.ngOnInit();
      api.getPage.calls.reset();
    });

    it('確認後應呼叫單圖刪除端點並以回傳值更新圖庫', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      component.currentEssentialOil = makeEssentialOil({
        id: 9,
        images: [{ id: 1, imagePath: 'a.png', sortOrder: 0, isPrimary: true }]
      });
      api.delete.and.returnValue(of(makeEssentialOil({ id: 9, images: [] })));

      component.deleteImage(1);

      expect(api.delete).toHaveBeenCalledWith('admin/essential-oils/9/images/1');
      expect(component.currentEssentialOil.images).toEqual([]);
      expect(api.getPage).toHaveBeenCalled();
    });

    it('取消確認時不應送出刪除', () => {
      spyOn(window, 'confirm').and.returnValue(false);
      component.currentEssentialOil = makeEssentialOil({ id: 9 });

      component.deleteImage(1);

      expect(api.delete).not.toHaveBeenCalled();
    });

    it('未在編輯中的資料（無 id）不應送出刪除', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      component.currentEssentialOil = {};

      component.deleteImage(1);

      expect(api.delete).not.toHaveBeenCalled();
    });

    it('刪除失敗時應把錯誤寫入 saveError', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      component.currentEssentialOil = makeEssentialOil({ id: 9 });
      api.delete.and.returnValue(throwError(() => ({ error: { message: '圖檔不存在' } })));

      component.deleteImage(1);

      expect(component.saveError).toBe('圖檔不存在');
    });
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

  it('應渲染精油列表', () => {
    api.getPage.and.returnValue(of(makePage([makeEssentialOil({ name: '檸檬精油' })])));

    component.ngOnInit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('檸檬精油');
  });
});