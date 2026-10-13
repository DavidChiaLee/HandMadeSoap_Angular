import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { CategoriesComponent } from './categories.component';
import { ApiService } from '../../shared/services/api.service';
import { makeCategory, mockApiService } from '../../testing/test-helpers';

describe('CategoriesComponent', () => {
  let fixture: ComponentFixture<CategoriesComponent>;
  let component: CategoriesComponent;
  let api: jasmine.SpyObj<ApiService>;

  beforeEach(async () => {
    api = mockApiService();
    await TestBed.configureTestingModule({
      imports: [CategoriesComponent, FormsModule],
      providers: [{ provide: ApiService, useValue: api }]
    }).compileComponents();

    fixture = TestBed.createComponent(CategoriesComponent);
    component = fixture.componentInstance;
  });

  describe('載入列表', () => {
    it('ngOnInit 應向 admin/categories 載入資料', () => {
      api.getList.and.returnValue(of([makeCategory()]));

      component.ngOnInit();

      expect(api.getList).toHaveBeenCalledWith('admin/categories');
      expect(component.categories.length).toBe(1);
    });

    it('載入成功後不應顯示錯誤訊息', () => {
      api.getList.and.returnValue(of([makeCategory()]));

      component.ngOnInit();

      expect(component.loadError).toBeFalse();
    });

    it('載入失敗時應設定 loadError 並清空列表', () => {
      spyOn(console, 'error');
      api.getList.and.returnValue(throwError(() => new Error('network')));
      component.categories = [makeCategory()];

      component.ngOnInit();

      expect(component.loadError).toBeTrue();
      expect(component.categories).toEqual([]);
    });

    it('loadError 為 true 時畫面應顯示失敗提示', () => {
      spyOn(console, 'error');
      api.getList.and.returnValue(throwError(() => new Error('network')));

      component.ngOnInit();
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('資料載入失敗');
    });
  });

  describe('新增 / 編輯 Modal', () => {
    it('openModal 應開啟 Modal 並清空表單與編輯旗標', () => {
      component.isEdit = true;
      component.currentCategory = { id: 5, name: '舊值' };

      component.openModal();

      expect(component.showModal).toBeTrue();
      expect(component.isEdit).toBeFalse();
      expect(component.currentCategory).toEqual({});
    });

    it('editCategory 應帶入資料並設為編輯模式', () => {
      const category = makeCategory({ id: 3, name: '滋潤型' });

      component.editCategory(category);

      expect(component.showModal).toBeTrue();
      expect(component.isEdit).toBeTrue();
      expect(component.currentCategory.name).toBe('滋潤型');
    });

    it('editCategory 應淺拷貝而非直接引用原物件', () => {
      const category = makeCategory({ id: 3, name: '滋潤型' });

      component.editCategory(category);
      component.currentCategory.name = '改名了';

      expect(category.name).toBe('滋潤型');
    });

    it('closeModal 應關閉 Modal', () => {
      component.showModal = true;

      component.closeModal();

      expect(component.showModal).toBeFalse();
    });
  });

  describe('儲存', () => {
    beforeEach(() => {
      api.getList.and.returnValue(of([]));
      spyOn(window, 'alert');
    });

    it('新增模式應以 POST 送出並重新載入列表', () => {
      api.post.and.returnValue(of(makeCategory()));
      component.currentCategory = { name: '新品分類' };
      component.isEdit = false;

      component.saveCategory();

      expect(api.post).toHaveBeenCalledWith('admin/categories', { name: '新品分類' });
      expect(api.getList).toHaveBeenCalledWith('admin/categories');
      expect(component.showModal).toBeFalse();
    });

    it('編輯模式且有 id 時應以 PUT 送出', () => {
      api.put.and.returnValue(of(makeCategory()));
      component.currentCategory = { id: 7, name: '改名' };
      component.isEdit = true;

      component.saveCategory();

      expect(api.put).toHaveBeenCalledWith('admin/categories/7', { id: 7, name: '改名' });
      expect(api.post).not.toHaveBeenCalled();
    });

    it('儲存失敗時應彈出錯誤訊息並保持 Modal 開啟', () => {
      api.post.and.returnValue(
        throwError(() => ({ error: { message: '分類已存在' } }))
      );
      component.isEdit = false;
      component.showModal = true;

      component.saveCategory();

      expect(window.alert).toHaveBeenCalledWith('新增分類失敗：分類已存在');
      expect(component.showModal).toBeTrue();
    });

    it('儲存失敗遇到 401 時不應彈出提示（由攔截器導向登入）', () => {
      api.post.and.returnValue(throwError(() => ({ status: 401 })));
      component.isEdit = false;

      component.saveCategory();

      expect(window.alert).not.toHaveBeenCalled();
    });
  });

  describe('刪除', () => {
    beforeEach(() => {
      api.getList.and.returnValue(of([]));
      api.delete.and.returnValue(of(null));
    });

    it('確認後應送出刪除並重新載入', () => {
      spyOn(window, 'confirm').and.returnValue(true);

      component.deleteCategory(3);

      expect(api.delete).toHaveBeenCalledWith('admin/categories/3');
      expect(api.getList).toHaveBeenCalledWith('admin/categories');
    });

    it('取消確認時不應送出刪除', () => {
      spyOn(window, 'confirm').and.returnValue(false);

      component.deleteCategory(3);

      expect(api.delete).not.toHaveBeenCalled();
    });

    it('刪除失敗時應彈出錯誤訊息', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      spyOn(window, 'alert');
      api.delete.and.returnValue(throwError(() => ({ error: { message: '分類已被使用' } })));

      component.deleteCategory(3);

      expect(window.alert).toHaveBeenCalledWith('刪除分類失敗：分類已被使用');
    });
  });

  it('應以列表呈現分類', () => {
    api.getList.and.returnValue(of([makeCategory({ name: '清潔型' })]));

    component.ngOnInit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('清潔型');
  });
});
