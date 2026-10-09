package com.handsoap.service;

import com.handsoap.dto.ContactRequest;
import com.handsoap.entity.Contact;
import com.handsoap.exception.DuplicateContactException;
import com.handsoap.exception.ResourceNotFoundException;
import com.handsoap.repository.ContactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactRepository contactRepository;

    private ContactService service;

    @BeforeEach
    void setUp() {
        service = new ContactService(contactRepository, 10);
    }

    @Test
    @DisplayName("窗口內重複內容（同 email+主旨+內容）應拋 DuplicateContactException 且不寫庫")
    void shouldRejectDuplicateContactWithinWindow() {
        given(contactRepository.existsByEmailIgnoreCaseAndSubjectAndMessageAndCreatedAtAfter(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)))
                .willReturn(true);

        assertThatThrownBy(() -> service.saveContact(request()))
                .isInstanceOf(DuplicateContactException.class)
                .hasMessageContaining("相同訊息");

        // 重複時絕不可寫庫（BDDMockito.then 沒有 shouldNever，改用原生 verify）
        verify(contactRepository, never()).save(any(Contact.class));
    }

    @Test
    @DisplayName("非重複內容應照常存檔（去重比對失敗時預設放行）")
    void shouldSaveNonDuplicateContact() {
        // exists 查詢未 stub → Mockito 佈林預設 false = 沒有重複
        given(contactRepository.save(any(Contact.class))).willAnswer(inv -> {
            Contact saved = inv.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        assertThat(service.saveContact(request()).getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("新增聯絡表單時 isReplied 應預設為 false")
    void shouldSaveContactAsUnreplied() {
        given(contactRepository.save(any(Contact.class))).willAnswer(inv -> {
            Contact saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        var saved = service.saveContact(request());

        ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
        then(contactRepository).should().save(captor.capture());
        assertThat(captor.getValue().getIsReplied()).isFalse();
        assertThat(captor.getValue().getName()).isEqualTo("王小明");
        assertThat(saved.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("取得全部聯絡資料時應使用 findAll")
    void shouldReturnAllContacts() {
        Pageable pageable = PageRequest.of(0, 5);
        given(contactRepository.findAll(pageable))
                .willReturn(new PageImpl<>(List.of(contact(1L, false))));

        assertThat(service.getAllContacts(pageable).getContent()).hasSize(1);
    }

    @Test
    @DisplayName("取得未回覆資料時應使用 findByIsReplied(false)")
    void shouldReturnUnrepliedContacts() {
        Pageable pageable = PageRequest.of(0, 5);
        given(contactRepository.findByIsReplied(false, pageable))
                .willReturn(new PageImpl<>(List.of(contact(2L, false))));

        assertThat(service.getUnrepliedContacts(pageable).getContent()).hasSize(1);
    }

    @Test
    @DisplayName("標記已回覆時應將 isReplied 設為 true")
    void shouldMarkContactAsReplied() {
        Contact contact = contact(3L, false);
        given(contactRepository.findById(3L)).willReturn(Optional.of(contact));
        given(contactRepository.save(any(Contact.class))).willAnswer(inv -> inv.getArgument(0));

        var updated = service.markAsReplied(3L);

        assertThat(updated.getIsReplied()).isTrue();
        then(contactRepository).should().save(contact);
    }

    @Test
    @DisplayName("標記不存在的資料為已回覆時應拋出 ResourceNotFoundException")
    void shouldThrowWhenMarkingMissingContact() {
        given(contactRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsReplied(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("更新聯絡資料時應覆蓋可變欄位並保留 id")
    void shouldUpdateContact() {
        Contact contact = contact(3L, false);
        given(contactRepository.findById(3L)).willReturn(Optional.of(contact));
        given(contactRepository.save(any(Contact.class))).willAnswer(inv -> inv.getArgument(0));

        ContactRequest update = new ContactRequest();
        update.setName("李大華");
        update.setEmail("lee@example.com");
        update.setPhone("0987654321");
        update.setSubject("修改後主旨");
        update.setMessage("修改後訊息");

        var result = service.updateContact(3L, update);

        assertThat(result.getId()).isEqualTo(3L);
        assertThat(result.getName()).isEqualTo("李大華");
        assertThat(result.getEmail()).isEqualTo("lee@example.com");
        assertThat(result.getSubject()).isEqualTo("修改後主旨");
        assertThat(result.getMessage()).isEqualTo("修改後訊息");
        then(contactRepository).should().save(contact);
    }

    @Test
    @DisplayName("更新不存在的資料時應拋出 ResourceNotFoundException")
    void shouldThrowWhenUpdatingMissingContact() {
        given(contactRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateContact(99L, request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("切換回覆狀態時應寫入指定的 isReplied")
    void shouldToggleRepliedStatus() {
        Contact contact = contact(4L, false);
        given(contactRepository.findById(4L)).willReturn(Optional.of(contact));
        given(contactRepository.save(any(Contact.class))).willAnswer(inv -> inv.getArgument(0));

        var updated = service.toggleReplied(4L, true);
        assertThat(updated.getIsReplied()).isTrue();

        var reverted = service.toggleReplied(4L, false);
        assertThat(reverted.getIsReplied()).isFalse();
    }

    @Test
    @DisplayName("切換不存在資料的回覆狀態時應拋出 ResourceNotFoundException")
    void shouldThrowWhenTogglingMissingContact() {
        given(contactRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleReplied(99L, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("刪除聯絡資料時應呼叫 repository.delete")
    void shouldDeleteContact() {
        Contact contact = contact(5L, false);
        given(contactRepository.findById(5L)).willReturn(Optional.of(contact));

        service.deleteContact(5L);

        then(contactRepository).should().delete(contact);
    }

    @Test
    @DisplayName("刪除不存在的資料時應拋出 ResourceNotFoundException")
    void shouldThrowWhenDeletingMissingContact() {
        given(contactRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteContact(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    private Contact contact(Long id, boolean replied) {
        return Contact.builder().id(id).name("王小明").email("a@b.com").isReplied(replied).build();
    }

    private ContactRequest request() {
        ContactRequest request = new ContactRequest();
        request.setName("王小明");
        request.setEmail("a@b.com");
        request.setPhone("0912345678");
        request.setSubject("訂單問題");
        request.setMessage("請問出貨時間");
        return request;
    }
}