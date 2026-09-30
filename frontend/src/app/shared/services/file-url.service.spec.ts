import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { PLACEHOLDER_IMAGE } from '../constants';
import { FileUrlService, toBaseUrl, pickPrimaryImage } from './file-url.service';

describe('FileUrlService', () => {
  let service: FileUrlService;
  // 測試環境讀 environment.ts（karma 不套用 production 的 fileReplacements）
  const base = toBaseUrl(environment.uploadUrl);

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(FileUrlService);
  });

  describe('url', () => {
    it('應把後端相對路徑接在環境設定的上傳目錄之後', () => {
      expect(service.url('soaps/20260101-a.png')).toBe(`${base}soaps/20260101-a.png`);
    });

    it('路徑帶前導斜線時不應產生雙斜線', () => {
      expect(service.url('/soaps/a.png')).toBe(`${base}soaps/a.png`);
    });

    it('空值應退回本機佔位圖', () => {
      expect(service.url(null)).toBe(PLACEHOLDER_IMAGE);
      expect(service.url(undefined)).toBe(PLACEHOLDER_IMAGE);
      expect(service.url('')).toBe(PLACEHOLDER_IMAGE);
    });

    it('已是絕對 URL 時應原樣沿用，不加上傳目錄前綴', () => {
      expect(service.url('https://cdn.example.com/a.png')).toBe('https://cdn.example.com/a.png');
      expect(service.url('http://cdn.example.com/a.png')).toBe('http://cdn.example.com/a.png');
      expect(service.url('//cdn.example.com/a.png')).toBe('//cdn.example.com/a.png');
    });

    it('data URI 應原樣沿用（後台選圖預覽用）', () => {
      const dataUri = 'data:image/png;base64,AAAA';
      expect(service.url(dataUri)).toBe(dataUri);
    });
  });

  describe('primaryUrl', () => {
    it('應取 images 第一張圖', () => {
      const images = [{ imagePath: 'soaps/1.png' }, { imagePath: 'soaps/2.png' }];
      expect(service.primaryUrl(images)).toBe(`${base}soaps/1.png`);
    });

    it('沒有圖片時應退回佔位圖', () => {
      expect(service.primaryUrl([])).toBe(PLACEHOLDER_IMAGE);
      expect(service.primaryUrl(null)).toBe(PLACEHOLDER_IMAGE);
      expect(service.primaryUrl(undefined)).toBe(PLACEHOLDER_IMAGE);
    });

    it('第一張圖的 imagePath 為空時應退回佔位圖', () => {
      expect(service.primaryUrl([{ imagePath: '' }])).toBe(PLACEHOLDER_IMAGE);
      expect(service.primaryUrl([{}])).toBe(PLACEHOLDER_IMAGE);
    });
  });

  describe('toBaseUrl', () => {
    it('缺少結尾斜線時應自動補上', () => {
      expect(toBaseUrl('/uploads')).toBe('/uploads/');
    });

    it('已有結尾斜線時不應重複補', () => {
      expect(toBaseUrl('/uploads/')).toBe('/uploads/');
    });
  });

  describe('pickPrimaryImage / primaryUrl 主圖選擇', () => {
    it('應優先取 isPrimary 為 true 的圖片，而非陣列第一張', () => {
      const images = [
        { imagePath: 'soaps/1.png', sortOrder: 1, isPrimary: false },
        { imagePath: 'soaps/2.png', sortOrder: 9, isPrimary: true }
      ];
      expect(pickPrimaryImage(images)?.imagePath).toBe('soaps/2.png');
      expect(service.primaryUrl(images)).toBe(`${base}soaps/2.png`);
    });

    it('都沒有 isPrimary 時應取 sortOrder 最小者', () => {
      const images = [
        { imagePath: 'soaps/1.png', sortOrder: 5 },
        { imagePath: 'soaps/2.png', sortOrder: 1 }
      ];
      expect(service.primaryUrl(images)).toBe(`${base}soaps/2.png`);
    });

    it('isPrimary 與 sortOrder 都相同時應維持原順序（與舊行為 images[0] 一致）', () => {
      const images = [
        { imagePath: 'soaps/1.png', sortOrder: 0, isPrimary: true },
        { imagePath: 'soaps/2.png', sortOrder: 0, isPrimary: true }
      ];
      expect(service.primaryUrl(images)).toBe(`${base}soaps/1.png`);
    });

    it('sortOrder 未設定時應視為 0', () => {
      const images = [
        { imagePath: 'soaps/1.png' },
        { imagePath: 'soaps/2.png', sortOrder: 3 }
      ];
      expect(service.primaryUrl(images)).toBe(`${base}soaps/1.png`);
    });

    it('不應更動傳入的陣列', () => {
      const images = [
        { imagePath: 'soaps/1.png', sortOrder: 5 },
        { imagePath: 'soaps/2.png', sortOrder: 1 }
      ];
      service.primaryUrl(images);
      expect(images.map(i => i.imagePath)).toEqual(['soaps/1.png', 'soaps/2.png']);
    });

    it('空陣列或 null 時應回傳 null / 佔位圖', () => {
      expect(pickPrimaryImage([])).toBeNull();
      expect(pickPrimaryImage(null)).toBeNull();
      expect(service.primaryUrl([])).toBe(PLACEHOLDER_IMAGE);
    });
  });
});
