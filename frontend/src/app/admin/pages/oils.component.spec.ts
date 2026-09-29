import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ElementRef } from '@angular/core';
import { of, throwError } from 'rxjs';
import { OilsComponent } from './oils.component';
import { ApiService } from '../../shared/services/api.service';
import {
  fakeFile,
  fakeFileEvent,
  makeOil,
  makePage,
  mockApiService,
  spyComputedStyle,
  stubElementRef,
  stubFileReader
} from '../../testing/test-helpers';
import { MAX_UPLOAD_SIZE, UPLOAD_SIZE_ERROR_MSG } from '../../shared/constants';

describe('OilsComponent (admin)', () => {
  let fixture: ComponentFixture<OilsComponent>;
  let component: OilsComponent;
  let api: jasmine.SpyObj<ApiService>;

  beforeEach(async () => {
    api = mockApiService();
    api.getPage.and.returnValue(of(makePage([makeOil()], { totalPages: 3, number: 0 })));

    await TestBed.configureTestingModule({
      imports: [OilsComponent, FormsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: ElementRef, useValue: stubElementRef('auto') }
      ]
    }).compileComponents();

    spyComputedStyle('auto');

    fixture = TestBed.createComponent(OilsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => fixture.destroy());

  describe('載入列表', () => {
    it('ngOnInit 應以 page/size/sort 載入 admin/oils', () => {
      component.ngOnInit();

      const params = api.getPage.calls.mostRecent().args[1];
      expect(params?.get('page')).toBe('0');
      expect(params?.get('size')).toBe('5');
      expect(params?.get('sort')).toBe('id,desc');
    });

    it('有搜尋關鍵字時應附加 keyword 參數', () => {
      component.searchKeyword = '橄欖';

      component.loadOils();

      expect(api.getPage.calls.mostRecent().args[1]?.get('keyword')).toBe('橄欖');
    });

    it('關鍵字只有空白時不應附加 keyword 參數', () => {
      component.searchKeyword = '   ';

      component.loadOils();

      expect(api.getPage.calls.mostRecent().args[1]?.has('keyword')).toBeFalse();
    });

    it('載入成功後應寫入內容與分頁狀態', () => {
      api.getPage.and.returnValue(
        of(makePage([makeOil({ id: 1 })], { number: 1, totalPages: 4, totalElements: 20 }))
      );

      component.ngOnInit();

      expect(component.oils.length).toBe(1);
      expect(component.page).toBe(1);
      expect(component.totalPages).toBe(4);
      expect(component.totalElements).toBe(20);
    });

    it('載入失敗時應清空列表與分頁狀態', () => {
      spyOn(console, 'error');
      api.getPage.and.returnValue(throwError(() => new Error('network')));
      component.oils = [makeOil()];
      component.totalElements = 99;

      component.ngOnInit();

      expect(component.oils).toEqual([]);
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
      component.searchKeyword = '油';
      component.onSearchInput();
      tick(299);

      expect(api.getPage).not.toHaveBeenCalled();
      // 清掉尚未觸發的 timer，避免殘留到後續測試
      flush();
    }));

    it('輸入滿 300ms 後應以第 0 頁查詢', fakeAsync(() => {
      component.searchKeyword = '油';
      component.onSearchInput();
      tick(300);

      expect(api.getPage).toHaveBeenCalled();
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
      expect(api.getPage).toHaveBeenCalled();
      expect(api.getPage.calls.mostRecent().args[1]?.has('keyword')).toBeFalse();
    });
  });
  describe('新增 / 編輯 Modal', () => {
    it('openModal 應清空所有欄位並設為新增模式', () => {
      component.isEdit = true;
      component.currentOil = { id: 5, name: '舊值' };
      component.selectedFile = fakeFile();
      component.fileError = 'err';
      component.saveError = 'err';

      component.openModal();

      expect(component.showModal).toBeTrue();
      expect(component.isEdit).toBeFalse();
      expect(component.currentOil).toEqual({});
      expect(component.selectedFile).toBeNull();
      expect(component.fileError).toBeNull();
      expect(component.saveError).toBeNull();
    });

    it('editOil 應帶入資料並設為編輯模式', () => {
      const oil = makeOil({ id: 3, name: '椰子油' });

      component.editOil(oil);

      expect(component.showModal).toBeTrue();
      expect(component.isEdit).toBeTrue();
      expect(component.currentOil.name).toBe('椰子油');
    });

    it('closeModal 應關閉 Modal', () => {
      component.showModal = true;

      component.closeModal();

      expect(component.showModal).toBeFalse();
    });
  });

  describe('儲存', () => {
    it('新增模式應以 POST 送出', () => {
      api.post.and.returnValue(of(makeOil({ id: 9 })));
      component.currentOil = { name: '新品' };
      component.isEdit = false;

      component.saveOil();

      expect(api.post).toHaveBeenCalledWith('admin/oils', { name: '新品' });
      expect(component.showModal).toBeFalse();
    });

    it('編輯模式且有 id 時應以 PUT 送出', () => {
      api.put.and.returnValue(of(makeOil()));
      component.currentOil = { id: 7, name: '改名' };
      component.isEdit = true;

      component.saveOil();

      expect(api.put).toHaveBeenCalledWith('admin/oils/7', { id: 7, name: '改名' });
      expect(api.post).not.toHaveBeenCalled();
    });

    it('儲存失敗時應設定 saveError 並保持 Modal 開啟', () => {
      api.post.and.returnValue(throwError(() => ({ error: { message: '名稱重複' } })));
      component.isEdit = false;
      component.showModal = true;

      component.saveOil();

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
      complete('data:image/png;base64,AAA');

      expect(reader.readAsDataURL).toHaveBeenCalled();
      expect(component.selectedImagePreview).toBe('data:image/png;base64,AAA');
    });

    it('未選取檔案時不應變更狀態', () => {
      const before = component.selectedFile;

      component.onFileSelected(fakeFileEvent(null));

      expect(component.selectedFile).toBe(before);
    });

    it('儲存後有選檔時應呼叫上傳端點', () => {
      api.post.and.returnValue(of(makeOil({ id: 9 })));
      component.selectedFile = fakeFile();
      component.isEdit = false;

      component.saveOil();

      expect(api.upload).toHaveBeenCalledWith('admin/oils/9/images', component.selectedFile, {
        sortOrder: '0',
        isPrimary: 'true'
      });
    });

    it('儲存後未選檔時不應呼叫上傳', () => {
      api.post.and.returnValue(of(makeOil({ id: 9 })));
      component.selectedFile = null;
      component.isEdit = false;

      component.saveOil();

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

      component.deleteOil(3);

      expect(api.delete).toHaveBeenCalledWith('admin/oils/3');
      expect(api.getPage).toHaveBeenCalled();
    });

    it('取消確認時不應送出刪除', () => {
      spyOn(window, 'confirm').and.returnValue(false);

      component.deleteOil(3);

      expect(api.delete).not.toHaveBeenCalled();
    });

    it('刪除最後一筆且非首頁時應自動回到上一頁', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      component.oils = [makeOil()];
      component.page = 2;

      component.deleteOil(3);

      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('1');
    });

    it('刪除後仍有其他資料時應留在原頁', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      component.oils = [makeOil(), makeOil({ id: 2 })];
      component.page = 1;

      component.deleteOil(3);

      expect(api.getPage.calls.mostRecent().args[1]?.get('page')).toBe('1');
    });
  });

  describe('捲動監聽', () => {
    /**
     * ElementRef 由框架自動注入指向真實 host element，findScrollContainer()
     * 會沿真實 DOM 往上找而找不到捲動容器，因此這裡直接指定可控的容器物件，
     * 專注驗證 onScroll / ngOnDestroy 的行為。
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
      container.scrollTop = 100;

      component.onScroll();

      expect(component.showScrollTop).toBeFalse();
    });

    it('scrollToTop 應以平滑捲動方式回到頂端', () => {
      const container = attachContainer();

      component.scrollToTop();

      expect(container.scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' });
    });

    it('ngOnDestroy 應移除捲動監聽', () => {
      const container = attachContainer();

      component.ngOnDestroy();

      expect(container.removeEventListener).toHaveBeenCalled();
    });
  });

  it('應渲染油品列表', () => {
    api.getPage.and.returnValue(of(makePage([makeOil({ name: '甜杏仁油' })])));

    component.ngOnInit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('甜杏仁油');
  });
});
