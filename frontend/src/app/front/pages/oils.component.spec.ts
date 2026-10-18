import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { OilsComponent } from './oils.component';
import { OilListConfig } from '../components/oil-search-list.component';
import { ApiService } from '../../shared/services/api.service';
import { mockApiService } from '../../testing/test-helpers';

describe('OilsComponent', () => {
  let fixture: ComponentFixture<OilsComponent>;
  let component: OilsComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OilsComponent, FormsModule],
      providers: [provideRouter([]), { provide: ApiService, useValue: mockApiService() }]
    }).compileComponents();

    fixture = TestBed.createComponent(OilsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => fixture?.destroy());

  it('應設定正確的 oilConfig', () => {
    expect(component.oilConfig).toBeDefined();
    expect(component.oilConfig.title).toBe('天然油品');
    expect(component.oilConfig.placeholder).toBe('輸入關鍵字搜尋油品名稱');
    expect(component.oilConfig.emptyWithKeyword).toBe('查無符合條件的油品');
    expect(component.oilConfig.emptyWithoutKeyword).toBe('查無油品資料');
    expect(component.oilConfig.detailField).toBe('benefits');
    expect(component.oilConfig.routePrefix).toBe('/oils');
    expect(component.oilConfig.baseEndpoint).toBe('oils');
  });

  it('應渲染 OilSearchListComponent', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-oil-search-list')).toBeTruthy();
  });
});
