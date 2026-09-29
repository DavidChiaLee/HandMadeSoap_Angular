import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { EssentialOilDetailComponent } from './essential-oil-detail.component';
import { ApiService } from '../../shared/services/api.service';
import { EssentialOil } from '../../shared/models/essential-oil.model';
import {
  makeEssentialOil,
  mockApiService,
  stubRoute
} from '../../testing/test-helpers';

describe('EssentialOilDetailComponent', () => {
  let fixture: ComponentFixture<EssentialOilDetailComponent>;
  let component: EssentialOilDetailComponent;
  let api: jasmine.SpyObj<ApiService>;

  beforeEach(async () => {
    api = mockApiService();
    await TestBed.configureTestingModule({
      imports: [EssentialOilDetailComponent],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: ActivatedRoute, useValue: stubRoute('7') }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(EssentialOilDetailComponent);
    component = fixture.componentInstance;
  });

  it('應建立元件', () => {
    expect(component).toBeTruthy();
  });

  it('應以路由參數 id 呼叫 essential-oils/{id}', () => {
    api.get.and.returnValue(of(makeEssentialOil({ id: 7 })));

    component.ngOnInit();

    expect(api.get).toHaveBeenCalledWith('essential-oils/7');
    expect(component.essentialOil?.id).toBe(7);
  });

  it('請求失敗時應退回展示用假資料', () => {
    api.get.and.returnValue(throwError(() => new Error('network')));

    component.ngOnInit();

    expect(component.essentialOil).toBeTruthy();
    expect(component.essentialOil?.name).toBe('薰衣草精油');
  });

  it('請求尚未回應時應顯示載入中訊息', () => {
    // 用未發出的 Subject 讓請求保持 pending，模擬等待後端回應的狀態
    api.get.and.returnValue(new Subject<EssentialOil>());

    component.ngOnInit();
    fixture.detectChanges();

    expect(component.essentialOil).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('載入中');
  });

  it('路由無 id 時不應發出請求且維持載入中', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [EssentialOilDetailComponent],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: ActivatedRoute, useValue: stubRoute(null) }
      ]
    });
    const f = TestBed.createComponent(EssentialOilDetailComponent);

    f.componentInstance.ngOnInit();
    f.detectChanges();

    expect(api.get).not.toHaveBeenCalled();
    expect(f.nativeElement.textContent).toContain('載入中');
  });

  it('載入成功後應渲染精油名稱與注意事項', () => {
    api.get.and.returnValue(
      of(makeEssentialOil({ name: '茶樹精油', precautions: '需稀釋後使用' }))
    );

    component.ngOnInit();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('茶樹精油');
    expect(text).toContain('需稀釋後使用');
  });
});
