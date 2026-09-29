import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { ApiService } from '../../shared/services/api.service';
import { mockApiService } from '../../testing/test-helpers';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let api: jasmine.SpyObj<ApiService>;

  beforeEach(async () => {
    api = mockApiService();
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideRouter([]), { provide: ApiService, useValue: api }]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
  });

  it('初始統計值應全部為 0', () => {
    expect(component.stats).toEqual({ soaps: 0, oils: 0, essentialOils: 0, contacts: 0 });
  });

  it('ngOnInit 應向 admin/stats 端點載入統計', () => {
    api.get.and.returnValue(of({ soaps: 1, oils: 2, essentialOils: 3, contacts: 4 }));

    component.ngOnInit();

    expect(api.get).toHaveBeenCalledWith('admin/stats');
    expect(component.stats).toEqual({ soaps: 1, oils: 2, essentialOils: 3, contacts: 4 });
  });

  it('載入失敗時應將統計歸零並記錄錯誤', () => {
    spyOn(console, 'error');
    api.get.and.returnValue(throwError(() => new Error('network')));
    component.stats = { soaps: 9, oils: 9, essentialOils: 9, contacts: 9 };

    component.ngOnInit();

    expect(component.stats).toEqual({ soaps: 0, oils: 0, essentialOils: 0, contacts: 0 });
    expect(console.error).toHaveBeenCalled();
  });

  it('應在畫面顯示各項統計數字', () => {
    api.get.and.returnValue(of({ soaps: 12, oils: 8, essentialOils: 5, contacts: 3 }));

    component.ngOnInit();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('12');
    expect(text).toContain('8');
    expect(text).toContain('5');
    expect(text).toContain('3');
  });
});
