import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { HttpParams } from '@angular/common/http';
import { ContactsComponent } from './contacts.component';
import { ApiService } from '../../shared/services/api.service';
import { makeContact, makePage, mockApiService } from '../../testing/test-helpers';

describe('ContactsComponent', () => {
  let fixture: ComponentFixture<ContactsComponent>;
  let component: ContactsComponent;
  let api: jasmine.SpyObj<ApiService>;

  beforeEach(async () => {
    api = mockApiService();
    await TestBed.configureTestingModule({
      imports: [ContactsComponent, FormsModule],
      providers: [{ provide: ApiService, useValue: api }]
    }).compileComponents();

    fixture = TestBed.createComponent(ContactsComponent);
    component = fixture.componentInstance;
  });

  describe('載入列表', () => {
    beforeEach(() => {
      api.getPage.and.returnValue(of(makePage([makeContact()], { totalElements: 1, totalPages: 1 })));
    });

    it('ngOnInit 應以分頁參數向 admin/contacts 載入資料', () => {
      component.ngOnInit();

      const params = api.getPage.calls.mostRecent().args[1] as HttpParams;
      expect(params?.get('page')).toBe('0');
      expect(params?.get('size')).toBe('20');
      expect(params?.get('sort')).toBe('createdAt,desc');
      expect(api.getPage).toHaveBeenCalledWith('admin/contacts', params);
      expect(component.contacts.length).toBe(1);
    });

    it('載入失敗時應記錄錯誤並清空列表', () => {
      spyOn(console, 'error');
      api.getPage.and.returnValue(throwError(() => new Error('network')));
      component.contacts = [makeContact()];
      component.totalPages = 3;
      component.totalElements = 50;

      component.ngOnInit();

      expect(component.contacts).toEqual([]);
      expect(component.totalPages).toBe(0);
      expect(component.totalElements).toBe(0);
      expect(console.error).toHaveBeenCalled();
    });
  });

  describe('分頁', () => {
    beforeEach(() => {
      api.getPage.and.returnValue(of(makePage([], { number: 0, totalElements: 0, totalPages: 5 })));
      component.page = 0;
      component.totalPages = 5;
      component.totalElements = 100;
    });

    it('應依 page、size、sort 參數發送分頁請求', () => {
      component.loadContacts(2);

      const params = api.getPage.calls.mostRecent().args[1] as HttpParams;
      expect(params?.get('page')).toBe('2');
      expect(params?.get('size')).toBe('20');
      expect(params?.get('sort')).toBe('createdAt,desc');
      expect(api.getPage).toHaveBeenCalledWith('admin/contacts', params);
    });

    it('應使用目前 page 重新載入', () => {
      component.totalPages = 5;
      component.goToPage(3);

      expect(api.getPage).toHaveBeenCalledWith('admin/contacts', jasmine.any(HttpParams));
    });

    it('上一頁按鈕應可切換到前一頁', () => {
      component.page = 2;
      component.goToPage(1);

      expect(api.getPage).toHaveBeenCalledWith('admin/contacts', jasmine.any(HttpParams));
    });

    it('下一頁按鈕應可切換到後一頁', () => {
      component.page = 2;
      component.goToPage(3);

      expect(api.getPage).toHaveBeenCalledWith('admin/contacts', jasmine.any(HttpParams));
    });

    it('超出範圍的分頁不應觸發載入', () => {
      component.page = 0;
      component.goToPage(-1);
      component.goToPage(100);

      expect(api.getPage).not.toHaveBeenCalled();
    });

    it('應同步回傳的 number、totalPages、totalElements', () => {
      api.getPage.and.returnValue(of(makePage([makeContact()], {
        number: 3,
        totalPages: 5,
        totalElements: 95
      })));

      component.loadContacts(3);

      expect(component.page).toBe(3);
      expect(component.totalPages).toBe(5);
      expect(component.totalElements).toBe(95);
      expect(component.contacts.length).toBe(1);
    });
  });

  describe('查看詳情', () => {
    it('viewContact 應開啟 Modal 並帶入該筆資料', () => {
      const contact = makeContact({ id: 9, name: '李小美' });

      component.viewContact(contact);

      expect(component.showModal).toBeTrue();
      expect(component.selectedContact).toBe(contact);
    });

    it('closeModal 應關閉 Modal', () => {
      component.showModal = true;

      component.closeModal();

      expect(component.showModal).toBeFalse();
    });

    it('Modal 開啟時應顯示訊息內容', () => {
      api.getPage.and.returnValue(of(makePage([])));
      component.ngOnInit();
      component.viewContact(makeContact({ message: '請協助確認訂單' }));
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('請協助確認訂單');
    });

    it('電話為空時應顯示佔位符', () => {
      api.getPage.and.returnValue(of(makePage([])));
      component.ngOnInit();
      component.viewContact(makeContact({ phone: '' }));
      fixture.detectChanges();

      const spans = fixture.nativeElement.querySelectorAll(
        '.fixed span'
      ) as NodeListOf<HTMLSpanElement>;
      expect(Array.from(spans).some(s => s.textContent?.trim() === '-')).toBeTrue();
    });
  });

  describe('切換回覆狀態', () => {
    beforeEach(() => {
      api.getPage.and.returnValue(of(makePage([makeContact()])));
      api.put.and.returnValue(of(makeContact()));
    });

    it('未回覆的訊息應以 isReplied=true 呼叫切換端點', () => {
      const contact = makeContact({ id: 1, isReplied: false });

      component.toggleReplied(contact);

      const [url, , params] = api.put.calls.mostRecent().args as [string, any, HttpParams];
      expect(url).toBe('admin/contacts/1/replied-status');
      expect(params.get('isReplied')).toBe('true');
    });

    it('已回覆的訊息應以 isReplied=false 呼叫切換端點', () => {
      const contact = makeContact({ id: 1, isReplied: true });

      component.toggleReplied(contact);

      const params = api.put.calls.mostRecent().args[2] as HttpParams;
      expect(params.get('isReplied')).toBe('false');
    });

    it('切換時不應送出整筆 Contact 作為 body', () => {
      component.toggleReplied(makeContact({ id: 1 }));

      expect(api.put.calls.mostRecent().args[1]).toBeNull();
    });

    it('切換後應重新載入目前頁', () => {
      component.page = 2;
      component.toggleReplied(makeContact({ id: 1 }));

      expect(api.getPage).toHaveBeenCalledWith('admin/contacts', jasmine.any(HttpParams));
    });

    it('切換失敗時不應中斷流程且應設定錯誤訊息', () => {
      spyOn(console, 'error');
      api.put.and.returnValue(throwError(() => ({ error: { message: '更新失敗' } })));

      expect(() => component.toggleReplied(makeContact({ id: 1 }))).not.toThrow();
      expect(console.error).toHaveBeenCalled();
      expect(component.actionError).toBe('更新失敗');
    });
  });

  describe('刪除', () => {
    beforeEach(() => {
      api.getPage.and.returnValue(of(makePage([])));
      api.delete.and.returnValue(of(null));
    });

    it('確認後應送出刪除並重新載入目前頁', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      component.page = 2;

      component.deleteContact(5);

      expect(api.delete).toHaveBeenCalledWith('admin/contacts/5');
      expect(api.getPage).toHaveBeenCalledWith('admin/contacts', jasmine.any(HttpParams));
    });

    it('取消確認時不應送出刪除', () => {
      spyOn(window, 'confirm').and.returnValue(false);

      component.deleteContact(5);

      expect(api.delete).not.toHaveBeenCalled();
      expect(api.getPage).not.toHaveBeenCalled();
    });

    it('刪除失敗時應記錄錯誤並設定錯誤訊息', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      spyOn(console, 'error');
      api.delete.and.returnValue(throwError(() => ({ error: { message: '刪除失敗' } })));

      component.deleteContact(5);

      expect(console.error).toHaveBeenCalled();
      expect(component.actionError).toBe('刪除失敗');
    });
  });

  it('應依回覆狀態顯示不同標籤', () => {
    api.getPage.and.returnValue(of(makePage([
      makeContact({ id: 1, isReplied: true }),
      makeContact({ id: 2, isReplied: false })
    ])));

    component.ngOnInit();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('已回覆');
    expect(text).toContain('未回覆');
  });
});
