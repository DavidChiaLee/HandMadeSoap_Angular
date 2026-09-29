package com.handsoap.service;

import com.handsoap.dto.CategoryRequest;
import com.handsoap.entity.Category;
import com.handsoap.exception.BadRequestException;
import com.handsoap.exception.ResourceNotFoundException;
import com.handsoap.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository);
    }

    @Test
    @DisplayName("查無 id 對應資料時應拋出 ResourceNotFoundException")
    void shouldThrowWhenCategoryNotFound() {
        given(categoryRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCategoryById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("查得到資料時應回傳 DTO")
    void shouldReturnCategoryById() {
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category(1L, "一般皂")));

        assertThat(service.getCategoryById(1L).getName()).isEqualTo("一般皂");
    }

    @Test
    @DisplayName("取得全部資料時應使用 findAll")
    void shouldReturnAllCategories() {
        Pageable pageable = PageRequest.of(0, 5);
        given(categoryRepository.findAll(pageable))
                .willReturn(new PageImpl<>(List.of(category(1L, "一般皂"))));

        assertThat(service.getAllCategories(pageable).getContent()).hasSize(1);
    }

    @Test
    @DisplayName("新增時名稱重複應拋出 BadRequestException")
    void shouldRejectDuplicateNameOnCreate() {
        given(categoryRepository.existsByName("一般皂")).willReturn(true);

        assertThatThrownBy(() -> service.createCategory(request("一般皂")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");

        then(categoryRepository).should(never()).save(any(Category.class));
    }

    @Test
    @DisplayName("新增時 isActive 為 null 應預設為 true")
    void shouldDefaultIsActiveOnCreate() {
        given(categoryRepository.existsByName("新品類")).willReturn(false);
        given(categoryRepository.save(any(Category.class))).willAnswer(inv -> {
            Category saved = inv.getArgument(0);
            saved.setId(9L);
            return saved;
        });

        assertThat(service.createCategory(request("新品類")).getIsActive()).isTrue();
    }

    @Test
    @DisplayName("更新時名稱未變更不應觸發重複檢查")
    void shouldSkipDuplicateCheckWhenNameUnchanged() {
        Category existing = category(1L, "一般皂");
        given(categoryRepository.findById(1L)).willReturn(Optional.of(existing));
        given(categoryRepository.save(any(Category.class))).willAnswer(inv -> inv.getArgument(0));

        service.updateCategory(1L, request("一般皂"));

        then(categoryRepository).should(never()).existsByName(any());
    }

    @Test
    @DisplayName("更新時名稱改為既有值應拋出 BadRequestException")
    void shouldRejectDuplicateNameOnUpdate() {
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category(1L, "一般皂")));
        given(categoryRepository.existsByName("已存在")).willReturn(true);

        assertThatThrownBy(() -> service.updateCategory(1L, request("已存在")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("更新不存在的資料應拋出 ResourceNotFoundException")
    void shouldThrowWhenUpdatingMissingCategory() {
        given(categoryRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCategory(99L, request("新名")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("更新時 isActive 為 null 應保留原值")
    void shouldKeepIsActiveWhenUpdateOmitsIt() {
        Category existing = category(1L, "一般皂");
        existing.setIsActive(false);
        given(categoryRepository.findById(1L)).willReturn(Optional.of(existing));
        given(categoryRepository.save(any(Category.class))).willAnswer(inv -> inv.getArgument(0));

        service.updateCategory(1L, request("一般皂改名"));

        assertThat(existing.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("刪除不存在的資料應拋出 ResourceNotFoundException")
    void shouldThrowWhenDeletingMissingCategory() {
        given(categoryRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCategory(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("刪除成功時應呼叫 repository.delete")
    void shouldDeleteExistingCategory() {
        Category existing = category(1L, "一般皂");
        given(categoryRepository.findById(1L)).willReturn(Optional.of(existing));

        service.deleteCategory(1L);

        then(categoryRepository).should().delete(existing);
    }

    private Category category(Long id, String name) {
        return Category.builder().id(id).name(name).description("說明").sortOrder(1).isActive(true).build();
    }

    private CategoryRequest request(String name) {
        CategoryRequest request = new CategoryRequest();
        request.setName(name);
        request.setDescription("說明");
        request.setSortOrder(1);
        return request;
    }
}