import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { EssentialOilsComponent } from './essential-oils.component';
import { OilListConfig } from '../components/oil-search-list.component';
import { ApiService } from '../../shared/services/api.service';
import { mockApiService } from '../../testing/test-helpers';

describe('EssentialOilsComponent', () => {
  let fixture: ComponentFixture<EssentialOilsComponent>;
  let component: EssentialOilsComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EssentialOilsComponent, FormsModule],
      providers: [provideRouter([]), { provide: ApiService, useValue: mockApiService() }]
    }).compileComponents();

    fixture = TestBed.createComponent(EssentialOilsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => fixture?.destroy());

  it('應設定正確的 oilConfig', () => {
    expect(component.oilConfig).toBeDefined();
    expect(component.oilConfig.title).toBe('天然精油');
    expect(component.oilConfig.placeholder).toBe('輸入關鍵字搜尋精油名稱');
    expect(component.oilConfig.emptyWithKeyword).toBe('查無符合條件的精油');
    expect(component.oilConfig.emptyWithoutKeyword).toBe('查無精油資料');
    expect(component.oilConfig.detailField).toBe('scentNotes');
    expect(component.oilConfig.routePrefix).toBe('/essential-oils');
    expect(component.oilConfig.baseEndpoint).toBe('essential-oils');
  });

  it('應渲染 OilSearchListComponent', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-oil-search-list')).toBeTruthy();
  });
});
