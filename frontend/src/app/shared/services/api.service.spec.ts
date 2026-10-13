import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { HttpParams } from '@angular/common/http';
import { ApiService, isPage } from './api.service';
import { makePage, makeSoap } from '../../testing/test-helpers';

describe('ApiService', () => {
  let service: ApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  describe('isPage 型別守衛（取代舊版 unwrap 結構猜測）', () => {
    it('完整 Page 結構應回傳 true', () => {
      expect(isPage(makePage([makeSoap()]))).toBeTrue();
    });

    it('陣列不應被判定為 Page', () => {
      expect(isPage([makeSoap()])).toBeFalse();
    });

    it('一般物件（無 content）應回傳 false', () => {
      expect(isPage(makeSoap())).toBeFalse();
    });

    it('有 content 但缺分頁欄位應回傳 false', () => {
      expect(isPage({ content: [makeSoap()] })).toBeFalse();
    });

    it('null/undefined 應回傳 false', () => {
      expect(isPage(null)).toBeFalse();
      expect(isPage(undefined)).toBeFalse();
    });
  });

  describe('get 契約（非 Page 單筆／陣列原樣）', () => {
    it('data 為一般物件時原樣回傳', done => {
      const soap = makeSoap();
      service.get<any>('soaps/1').subscribe(result => {
        expect(result).toBe(soap);
        done();
      });
      httpMock.expectOne(req => req.url.endsWith('/soaps/1'))
        .flush({ success: true, data: soap });
    });

    it('data 為陣列時原樣回傳陣列', done => {
      const list = [makeSoap({ id: 1 }), makeSoap({ id: 2 })];
      service.get<any>('soaps').subscribe(result => {
        expect(result).toEqual(list);
        done();
      });
      httpMock.expectOne(req => req.url.endsWith('/soaps'))
        .flush({ success: true, data: list });
    });

    it('data 為 Page 結構時應拋錯並提示改用 getPage/getList（契約違規 fail-fast）', done => {
      service.get<any>('soaps').subscribe({
        error: (err) => {
          expect(err.message).toContain('getList');
          expect(err.message).toContain('getPage');
          done();
        }
      });
      httpMock.expectOne(req => req.url.endsWith('/soaps'))
        .flush({ success: true, data: makePage([makeSoap()], { totalPages: 3 }) });
    });
  });

  describe('getList 契約（Page → content 陣列）', () => {
    it('應拆出 content 陣列', done => {
      const list = [makeSoap({ id: 1 })];
      service.getList<any>('soaps').subscribe(result => {
        expect(result).toEqual(list);
        done();
      });
      httpMock.expectOne(req => req.url.endsWith('/soaps'))
        .flush({ success: true, data: makePage(list, { totalPages: 3 }) });
    });

    it('data 非 Page 結構時應拋錯（契約違規 fail-fast）', done => {
      service.getList<any>('soaps').subscribe({
        error: (err) => {
          expect(err.message).toContain('Page');
          done();
        }
      });
      httpMock.expectOne(req => req.url.endsWith('/soaps'))
        .flush({ success: true, data: [makeSoap()] });
    });
  });

  describe('getPage 契約（完整 Page 中繼資料）', () => {
    it('應保留完整 Page 結構而不解包 content', done => {
      const list = [makeSoap()];
      const page = makePage(list, { totalPages: 5, number: 2, totalElements: 50 });
      service.getPage<any>('soaps').subscribe(result => {
        expect(result.content).toEqual(list);
        expect(result.totalPages).toBe(5);
        expect(result.number).toBe(2);
        done();
      });
      httpMock.expectOne(req => req.url.endsWith('/soaps'))
        .flush({ success: true, data: page });
    });

    it('data 非 Page 結構時應拋錯（契約違規 fail-fast）', done => {
      service.getPage<any>('soaps').subscribe({
        error: (err) => {
          expect(err.message).toContain('Page');
          done();
        }
      });
      httpMock.expectOne(req => req.url.endsWith('/soaps'))
        .flush({ success: true, data: makeSoap() });
    });
  });

  describe('HttpParams 傳遞', () => {
    it('get 應將 page/size/sort 參數附加到請求', () => {
      const params = new HttpParams().set('page', 0).set('size', 8);
      service.get<any>('oils', params).subscribe();
      const req = httpMock.expectOne(r => r.url.endsWith('/oils'));
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('8');
      req.flush({ success: true, data: [] });
    });
  });

  describe('post / put / delete', () => {
    it('post 應以 POST 送出 body 並解包回應', done => {
      const payload = { name: '新皂' };
      const created = makeSoap({ name: '新皂' });
      service.post<any>('admin/soaps', payload).subscribe(result => {
        expect(result).toEqual(created);
        done();
      });
      const req = httpMock.expectOne(r => r.url.endsWith('/admin/soaps'));
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(payload);
      req.flush({ success: true, data: created });
    });

    it('put 應以 PUT 送出 body', done => {
      const payload = { name: '改名' };
      service.put<any>('admin/soaps/1', payload).subscribe(result => {
        expect(result).toEqual(makeSoap({ name: '改名' }));
        done();
      });
      const req = httpMock.expectOne(r => r.url.endsWith('/admin/soaps/1'));
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(payload);
      req.flush({ success: true, data: makeSoap({ name: '改名' }) });
    });

    it('delete 應以 DELETE 呼叫且 data 為 null 時回傳 null', done => {
      service.delete<any>('admin/soaps/1').subscribe(result => {
        expect(result).toBeNull();
        done();
      });
      const req = httpMock.expectOne(r => r.url.endsWith('/admin/soaps/1'));
      expect(req.request.method).toBe('DELETE');
      req.flush({ success: true, data: null });
    });
  });

  describe('upload', () => {
    // HttpTestingController 會把 FormData 序列化為字串再送出，File 物件會退化成
    // '[object File]'，因此改以「欄位存在」驗證檔案確實被帶上。
    it('應以 FormData 送出檔案與額外參數', () => {
      const file = new File(['x'], 'a.png', { type: 'image/png' });
      service.upload<any>('admin/soaps/1/images', file, { sortOrder: '0', isPrimary: 'true' })
        .subscribe();

      const req = httpMock.expectOne(r => r.url.endsWith('/admin/soaps/1/images'));
      expect(req.request.method).toBe('POST');
      const body = req.request.body as FormData;
      expect(body.has('file')).toBeTrue();
      expect(body.get('sortOrder')).toBe('0');
      expect(body.get('isPrimary')).toBe('true');
      req.flush({ success: true, data: makeSoap() });
    });

    it('未提供額外參數時仍應送出檔案欄位', () => {
      const file = new File(['x'], 'b.png', { type: 'image/png' });
      service.upload<any>('admin/oils/1/images', file).subscribe();

      const req = httpMock.expectOne(r => r.url.endsWith('/admin/oils/1/images'));
      expect((req.request.body as FormData).has('file')).toBeTrue();
      req.flush({ success: true, data: {} });
    });
  });

  describe('getErrorMessage', () => {
    it('data 為欄位錯誤物件時應合併所有欄位訊息', () => {
      const err = { error: { message: 'Bad Request', data: { name: '名稱必填', price: '價格錯誤' } } };
      expect(ApiService.getErrorMessage(err)).toBe('名稱必填；價格錯誤');
    });

    it('data 為陣列或非物件時應取回應的 message', () => {
      const err = { error: { message: '分類已存在' } };
      expect(ApiService.getErrorMessage(err)).toBe('分類已存在');
    });

    it('無任何訊息時應回傳 fallback', () => {
      expect(ApiService.getErrorMessage({}, '操作失敗')).toBe('操作失敗');
    });

    it('傳入 null/undefined 時應安全回傳 fallback', () => {
      expect(ApiService.getErrorMessage(null, '預設訊息')).toBe('預設訊息');
    });
  });
});
