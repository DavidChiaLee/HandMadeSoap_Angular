import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { ContactComponent } from './contact.component';
import { ApiService } from '../../shared/services/api.service';
import { mockApiService } from '../../testing/test-helpers';

describe('ContactComponent', () => {
  let fixture: ComponentFixture<ContactComponent>;
  let component: ContactComponent;
  let api: jasmine.SpyObj<ApiService>;

  const filledForm = () => {
    component.contactForm = {
      name: '王小明',
      email: 'a@b.com',
      phone: '0912345678',
      subject: '訂單問題',
      message: '請問出貨時間'
    };
  };

  beforeEach(async () => {
    api = mockApiService();
    await TestBed.configureTestingModule({
      imports: [ContactComponent, FormsModule],
      providers: [{ provide: ApiService, useValue: api }]
    }).compileComponents();

    fixture = TestBed.createComponent(ContactComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('應建立元件', () => {
    expect(component).toBeTruthy();
  });

  it('初始狀態不應顯示任何回饋訊息', () => {
    expect(component.submitted).toBeFalse();
    expect(fixture.nativeElement.textContent).not.toContain('感謝您的訊息');
  });

  it('送出時應將表單內容 POST 到 contacts', () => {
    api.post.and.returnValue(of({}));
    filledForm();

    component.onSubmit();

    expect(api.post).toHaveBeenCalledWith('contacts', {
      name: '王小明',
      email: 'a@b.com',
      phone: '0912345678',
      subject: '訂單問題',
      message: '請問出貨時間'
    });
  });

  it('送出成功時應設定 submitted 並清空表單', () => {
    api.post.and.returnValue(of({}));
    filledForm();

    component.onSubmit();

    expect(component.submitted).toBeTrue();
    expect(component.contactForm).toEqual({
      name: '',
      email: '',
      phone: '',
      subject: '',
      message: ''
    });
  });

  it('送出成功時畫面應顯示感謝訊息', () => {
    api.post.and.returnValue(of({}));
    filledForm();

    component.onSubmit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('感謝您的訊息');
  });

  it('送出失敗時不應顯示成功訊息（Bug 1 回歸測試）', () => {
    api.post.and.returnValue(throwError(() => ({ error: { message: '伺服器錯誤' } })));
    filledForm();

    component.onSubmit();
    fixture.detectChanges();

    expect(component.submitted).toBeFalse();
    expect(fixture.nativeElement.textContent).not.toContain('感謝您的訊息');
  });

  it('送出失敗時應保留使用者已輸入的內容', () => {
    api.post.and.returnValue(throwError(() => ({ error: { message: '伺服器錯誤' } })));
    filledForm();

    component.onSubmit();

    expect(component.contactForm.name).toBe('王小明');
    expect(component.contactForm.message).toBe('請問出貨時間');
  });
});
