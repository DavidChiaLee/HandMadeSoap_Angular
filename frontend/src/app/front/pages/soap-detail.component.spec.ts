import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { SoapDetailComponent } from './soap-detail.component';
import { ApiService } from '../../shared/services/api.service';
import { Soap } from '../../shared/models/soap.model';
import {
  makeSoap,
  makeSoapOilRef,
  mockApiService,
  stubRoute
} from '../../testing/test-helpers';

describe('SoapDetailComponent', () => {
  let fixture: ComponentFixture<SoapDetailComponent>;
  let component: SoapDetailComponent;
  let api: jasmine.SpyObj<ApiService>;
  let route: ActivatedRoute;

  const create = () => {
    fixture = TestBed.createComponent(SoapDetailComponent);
    component = fixture.componentInstance;
  };

  describe('載入商品', () => {
    beforeEach(async () => {
      api = mockApiService();
      route = stubRoute('5');
      await TestBed.configureTestingModule({
        imports: [SoapDetailComponent],
        providers: [
          { provide: ApiService, useValue: api },
          { provide: ActivatedRoute, useValue: route }
        ]
      }).compileComponents();
    });

    it('應以路由參數 id 呼叫 soaps/{id}', () => {
      api.get.and.returnValue(of(makeSoap({ id: 5 })));

      create();
      component.ngOnInit();

      expect(api.get).toHaveBeenCalledWith('soaps/5');
      expect(component.soap?.id).toBe(5);
    });

    it('路由無 id 時不應發出任何請求', () => {
      route = stubRoute(null);
      TestBed.overrideProvider(ActivatedRoute, { useValue: route });
      TestBed.createComponent(SoapDetailComponent);

      const c = TestBed.createComponent(SoapDetailComponent).componentInstance;
      c.ngOnInit();

      expect(api.get).not.toHaveBeenCalled();
    });

    it('請求失敗時應退回展示用假資料', () => {
      api.get.and.returnValue(throwError(() => new Error('network')));

      create();
      component.ngOnInit();

      expect(component.soap).toBeTruthy();
      expect(component.soap?.name).toBe('薰衣草舒緩皂');
    });

    it('請求尚未回應時應顯示載入中訊息', () => {
      // 用未發出的 Subject 讓請求保持 pending，模擬等待後端回應的狀態
      api.get.and.returnValue(new Subject<Soap>());

      create();
      component.ngOnInit();
      fixture.detectChanges();

      expect(component.soap).toBeNull();
      expect(fixture.nativeElement.textContent).toContain('載入中');
    });
  });

  describe('成分彙整', () => {
    beforeEach(async () => {
      api = mockApiService();
      route = stubRoute('1');
      await TestBed.configureTestingModule({
        imports: [SoapDetailComponent],
        providers: [
          { provide: ApiService, useValue: api },
          { provide: ActivatedRoute, useValue: route }
        ]
      }).compileComponents();
      create();
    });

    it('無油品與精油時應回傳空字串', () => {
      expect(component.getOilNames()).toBe('');
      expect(component.getEssentialOilNames()).toBe('');
    });

    it('soap 為 null 時不應拋出例外', () => {
      expect(component.getOilNames()).toBe('');
      expect(component.getEssentialOilNames()).toBe('');
    });

    it('多筆油品應以「、」串接名稱', () => {
      component.soap = makeSoap({
        oils: [makeSoapOilRef(1, '椰子油'), makeSoapOilRef(2, '橄欖油')]
      });

      expect(component.getOilNames()).toBe('椰子油、橄欖油');
    });

    it('單筆精油應直接回傳名稱', () => {
      component.soap = makeSoap({ essentialOils: [makeSoapOilRef(1, '薰衣草精油')] });

      expect(component.getEssentialOilNames()).toBe('薰衣草精油');
    });
  });

  it('應渲染商品名稱與分類', () => {
    api = mockApiService();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [SoapDetailComponent],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: ActivatedRoute, useValue: stubRoute('1') }
      ]
    });

    api.get.and.returnValue(
      of(makeSoap({ name: '玫瑰保濕皂', category: { id: 1, name: '滋潤型' } }))
    );
    const f = TestBed.createComponent(SoapDetailComponent);
    f.componentInstance.ngOnInit();
    f.detectChanges();

    const text = f.nativeElement.textContent as string;
    expect(text).toContain('玫瑰保濕皂');
    expect(text).toContain('滋潤型');
  });
});
