import { ElementRef } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { Page } from '../shared/services/api.service';
import { ApiService } from '../shared/services/api.service';
import { Category } from '../shared/models/category.model';
import { Contact } from '../shared/models/contact.model';
import { EssentialOil } from '../shared/models/essential-oil.model';
import { Oil } from '../shared/models/oil.model';
import { Soap, SoapOilRef } from '../shared/models/soap.model';

/**
 * 前端單元測試共用 fixture 工廠。
 *
 * 目的：避免 20 個 spec 各處重複造資料，統一在此維護預設值，
 * 各測試只需覆寫自己關心的欄位（`over`），其餘欄位自動補齊。
 */

/** 建立後端 Spring Page<T> 結構，供 ApiService.getPage 回傳 */
export function makePage<T>(content: T[], over: Partial<Page<T>> = {}): Page<T> {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    number: 0,
    size: 10,
    ...over
  };
}

export function makeSoap(over: Partial<Soap> = {}): Soap {
  return {
    id: 1,
    name: '薰衣草舒緩皂',
    shortDescription: '幫助放鬆身心',
    fullDescription: '完整描述',
    price: 180,
    weight: '100g',
    dimensions: '',
    usageInstructions: '搓揉起泡後沖洗',
    storageInstructions: '置於乾燥處',
    ingredients: '',
    features: '',
    oils: [],
    essentialOils: [],
    isFeatured: false,
    isActive: true,
    category: { id: 1, name: '滋潤型' },
    images: [],
    createdAt: new Date('2026-01-01T00:00:00Z'),
    updatedAt: new Date('2026-01-01T00:00:00Z'),
    ...over
  };
}

export function makeOil(over: Partial<Oil> = {}): Oil {
  return {
    id: 1,
    name: '橄欖油',
    origin: '西班牙',
    benefits: '深層滋潤',
    suitableFor: '乾性肌膚',
    isActive: true,
    images: [],
    createdAt: new Date('2026-01-01T00:00:00Z'),
    updatedAt: new Date('2026-01-01T00:00:00Z'),
    ...over
  };
}

export function makeEssentialOil(over: Partial<EssentialOil> = {}): EssentialOil {
  return {
    id: 1,
    name: '薰衣草精油',
    scentNotes: '舒緩放鬆',
    benefits: '幫助入睡',
    precautions: '孕婦避免使用',
    isActive: true,
    images: [],
    createdAt: new Date('2026-01-01T00:00:00Z'),
    updatedAt: new Date('2026-01-01T00:00:00Z'),
    ...over
  };
}

export function makeCategory(over: Partial<Category> = {}): Category {
  return {
    id: 1,
    name: '滋潤型',
    description: '滋潤肌膚',
    sortOrder: 1,
    isActive: true,
    createdAt: new Date('2026-01-01T00:00:00Z'),
    updatedAt: new Date('2026-01-01T00:00:00Z'),
    ...over
  };
}

export function makeContact(over: Partial<Contact> = {}): Contact {
  return {
    id: 1,
    name: '王小明',
    email: 'a@b.com',
    phone: '0912345678',
    subject: '訂單問題',
    message: '請問出貨時間',
    isReplied: false,
    createdAt: new Date('2026-01-01T00:00:00Z'),
    ...over
  };
}

/** 建立 Soap 內的油品 / 精油參照（SoapOilRef） */
export function makeSoapOilRef(id: number, name: string): SoapOilRef {
  return { id, name };
}

/** 建立可注入元件的 ActivatedRoute stub，提供 snapshot.paramMap.get('id') */
export function stubRoute(id: string | null = '1'): ActivatedRoute {
  return {
    snapshot: {
      paramMap: convertToParamMap(id === null ? {} : { id })
    }
  } as unknown as ActivatedRoute;
}

/**
 * 建立 ApiService 的 Jasmine spy。
 *
 * 元件測試一律使用此 stub + `of()` / `throwError()`，
 * 不啟用 HttpClientTestingModule，讓元件測試專注於元件邏輯，
 * HTTP 封裝細節則由 api.service.spec.ts 單獨驗證。
 */
export function mockApiService(): jasmine.SpyObj<ApiService> {
  const api = jasmine.createSpyObj<ApiService>('ApiService', [
    'get', 'getList', 'getPage', 'post', 'put', 'delete', 'upload'
  ]);
  // 預設讓所有方法回傳空結果，避免測試只驗證「是否被呼叫」時
  // 因拿到 undefined 而在 .subscribe() 拋錯中斷整個測試執行。
  api.get.and.returnValue(of([]));
  api.getList.and.returnValue(of([]));
  api.getPage.and.returnValue(of(makePage([])));
  api.post.and.returnValue(of({}));
  api.put.and.returnValue(of({}));
  api.delete.and.returnValue(of(null));
  api.upload.and.returnValue(of({}));
  return api;
}

/** 營造指定大小的假檔案，供 onFileSelected 測試使用 */
export function fakeFile(name = 'photo.png', sizeInBytes = 1024): File {
  const file = new File(['x'], name, { type: 'image/png' });
  Object.defineProperty(file, 'size', { value: sizeInBytes });
  return file;
}

/** 營造 file input 的 change 事件物件 */
export function fakeFileEvent(file: File | null): any {
  return { target: { files: file ? [file] : [], value: 'C:\\fakepath\\photo.png' } };
}

/**
 * 建立帶有可控 parentElement 的 ElementRef stub。
 *
 * 後台頁面會由 nativeElement.parentElement 往上找 overflowY 為
 * auto / scroll 的捲動容器。`spyComputedStyle` 會讓所有元素都回傳指定
 * 的 overflow-y，因此只要提供一個 parentElement 即可命中。
 */
export function stubElementRef(overflowY: string | null = 'auto'): ElementRef {
  // 建立完整的上層鏈：parentElement.parentElement 為 null，確保 findScrollContainer
  // 能在第一層就依 overflow-y 命中，或在未命中時安全結束
  const parent: any = {
    parentElement: null,
    scrollTop: 0,
    scrollTo: jasmine.createSpy('scrollTo')
  };
  const native: any = { parentElement: parent };
  native.__overflowY = overflowY;
  return new ElementRef(native);
}

/** 建立可控的 FileReader stub，回傳讀檔完成後要觸發的 onload */
export function stubFileReader(): { reader: any; complete: (result: string) => void } {
  const reader: any = {
    onload: null,
    readAsDataURL: jasmine.createSpy('readAsDataURL')
  };
  return {
    reader,
    complete: (result: string) => reader.onload({ target: { result } })
  };
}

/**
 * 讓 spyOn(getComputedStyle) 對指定元素回傳特定 overflow-y，
 * 使 findScrollContainer() 能依預期找到（或找不到）捲動容器。
 */
export function spyComputedStyle(overflowY: string): jasmine.Spy {
  return spyOn(window, 'getComputedStyle').and.callFake(
    () => ({ overflowY } as unknown as CSSStyleDeclaration)
  );
}

/** 讓 addEventListener / removeEventListener 可被 spy，且不會在 stub 上拋錯 */
export function spyScrollListener(container: any): jasmine.Spy {
  container.addEventListener = jasmine.createSpy('addEventListener');
  container.removeEventListener = jasmine.createSpy('removeEventListener');
  return container.removeEventListener;
}
