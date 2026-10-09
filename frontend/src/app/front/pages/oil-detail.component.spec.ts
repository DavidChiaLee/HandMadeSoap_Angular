import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { OilDetailComponent } from './oil-detail.component';
import { ApiService } from '../../shared/services/api.service';
import { Oil } from '../../shared/models/oil.model';
import { makeOil, mockApiService, stubRoute } from '../../testing/test-helpers';

describe('OilDetailComponent', () => {
  let fixture: ComponentFixture<OilDetailComponent>;
  let component: OilDetailComponent;
  let api: jasmine.SpyObj<ApiService>;

  beforeEach(async () => {
    api = mockApiService();
    await TestBed.configureTestingModule({
      imports: [OilDetailComponent],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: ActivatedRoute, useValue: stubRoute('3') }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OilDetailComponent);
    component = fixture.componentInstance;
  });

  it('應建立元件', () => {
    expect(component).toBeTruthy();
  });

  it('應以路由參數 id 呼叫 oils/{id}', () => {
    api.get.and.returnValue(of(makeOil({ id: 3 })));

    component.ngOnInit();

    expect(api.get).toHaveBeenCalledWith('oils/3');
    expect(component.oil?.id).toBe(3);
  });

  it('請求失敗時應顯示錯誤並可重試，而非餵假資料', () => {
    api.get.and.returnValue(throwError(() => new Error('network')));

    component.ngOnInit();

    expect(component.oil).toBeNull();
    expect(component.errorMessage).toBeTruthy();
  });

  it('請求尚未回應時應顯示載入中訊息', () => {
    // 用未發出的 Subject 讓請求保持 pending，模擬等待後端回應的狀態
    api.get.and.returnValue(new Subject<Oil>());

    component.ngOnInit();
    fixture.detectChanges();

    expect(component.oil).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('載入中');
  });

  it('路由無 id 時不應發出請求且維持載入中', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [OilDetailComponent],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: ActivatedRoute, useValue: stubRoute(null) }
      ]
    });
    const f = TestBed.createComponent(OilDetailComponent);

    f.componentInstance.ngOnInit();
    f.detectChanges();

    expect(api.get).not.toHaveBeenCalled();
    expect(f.nativeElement.textContent).toContain('載入中');
  });

  it('載入成功後應渲染油品名稱與產地', () => {
    api.get.and.returnValue(of(makeOil({ name: '椰子油', origin: '菲律賓' })));

    component.ngOnInit();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('椰子油');
    expect(text).toContain('菲律賓');
  });
});
