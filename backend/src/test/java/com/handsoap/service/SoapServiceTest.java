package com.handsoap.service;

import com.handsoap.entity.Category;
import com.handsoap.entity.EssentialOil;
import com.handsoap.entity.Oil;
import com.handsoap.entity.Soap;
import com.handsoap.exception.BadRequestException;
import com.handsoap.exception.ResourceNotFoundException;
import com.handsoap.repository.CategoryRepository;
import com.handsoap.repository.EssentialOilRepository;
import com.handsoap.repository.OilRepository;
import com.handsoap.repository.SoapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SoapServiceTest {

    @Mock
    private SoapRepository soapRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private OilRepository oilRepository;

    @Mock
    private EssentialOilRepository essentialOilRepository;

    @Mock
    private FileStorageService fileStorageService;

    private SoapService service;

    @BeforeEach
    void setUp() {
        service = new SoapService(soapRepository, categoryRepository, oilRepository,
                essentialOilRepository, fileStorageService);
    }

    @Test
    @DisplayName("名稱搜尋應把去除前後空白的關鍵字傳給 repository")
    void shouldSearchSoapsByNameWithTrimmedKeyword() {
        Pageable pageable = PageRequest.of(0, 5);
        given(soapRepository.searchActiveSoapsByName("薰衣草", pageable))
                .willReturn(pageOf(soap(1L, "薰衣草皂")));

        var result = service.searchSoapsByName("  薰衣草  ", pageable);

        then(soapRepository).should().searchActiveSoapsByName("薰衣草", pageable);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("多欄位搜尋應使用 searchActiveSoaps（前台端點）")
    void shouldDelegateToMultiFieldSearch() {
        Pageable pageable = PageRequest.of(0, 5);
        given(soapRepository.searchActiveSoaps("cedar", pageable))
                .willReturn(pageOf(soap(2L, "雪松皂")));

        assertThat(service.searchSoaps("cedar", pageable).getContent()).hasSize(1);
        then(soapRepository).should().searchActiveSoaps("cedar", pageable);
    }

    @Test
    @DisplayName("取得全部資料時應使用 findByIsActiveTrue")
    void shouldReturnAllActiveSoaps() {
        Pageable pageable = PageRequest.of(0, 5);
        given(soapRepository.findByIsActiveTrue(pageable))
                .willReturn(pageOf(soap(3L, "橄欖皂")));

        assertThat(service.getAllSoaps(pageable).getContent()).hasSize(1);
        then(soapRepository).should().findByIsActiveTrue(pageable);
    }

    @Test
    @DisplayName("精選清單應使用 findByIsFeaturedTrueAndIsActiveTrue")
    void shouldReturnFeaturedSoaps() {
        Pageable pageable = PageRequest.of(0, 5);
        given(soapRepository.findByIsFeaturedTrueAndIsActiveTrue(pageable))
                .willReturn(pageOf(soap(4L, "精選皂")));

        assertThat(service.getFeaturedSoaps(pageable).getContent()).hasSize(1);
    }

    @Test
    @DisplayName("依分類查詢應使用 findByCategoryIdAndIsActiveTrue")
    void shouldReturnSoapsByCategory() {
        Pageable pageable = PageRequest.of(0, 5);
        given(soapRepository.findByCategoryIdAndIsActiveTrue(7L, pageable))
                .willReturn(pageOf(soap(5L, "分類皂")));

        assertThat(service.getSoapsByCategory(7L, pageable).getContent()).hasSize(1);
    }

    @Test
    @DisplayName("查無 id 對應資料時應拋出 ResourceNotFoundException")
    void shouldThrowWhenSoapNotFound() {
        given(soapRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSoapById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("查得到資料時應回傳 DTO")
    void shouldReturnSoapById() {
        given(soapRepository.findById(1L)).willReturn(Optional.of(soap(1L, "薰衣草皂")));

        assertThat(service.getSoapById(1L).getName()).isEqualTo("薰衣草皂");
    }

    @Test
    @DisplayName("新增時分類不存在應拋出 BadRequestException")
    void shouldThrowWhenCategoryMissingOnCreate() {
        com.handsoap.dto.SoapRequest request = new com.handsoap.dto.SoapRequest();
        request.setName("新皂");
        request.setCategoryId(99L);
        given(categoryRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSoap(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid category ID");
    }

    @Test
    @DisplayName("刪除不存在的資料應拋出 ResourceNotFoundException")
    void shouldThrowWhenDeletingMissingSoap() {
        given(soapRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteSoap(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("刪除成功時應呼叫 repository.delete")
    void shouldDeleteExistingSoap() {
        Soap existing = soap(6L, "薄荷皂");
        given(soapRepository.findById(6L)).willReturn(Optional.of(existing));

        service.deleteSoap(6L);

        then(soapRepository).should().delete(existing);
    }

    private Soap soap(Long id, String name) {
        return Soap.builder()
                .id(id)
                .category(Category.builder().id(1L).name("一般皂").build())
                .name(name)
                .shortDescription("短描述")
                .fullDescription("完整描述")
                .price(new BigDecimal("380"))
                .isActive(true)
                .build();
    }

    private PageImpl<Soap> pageOf(Soap... soaps) {
        return new PageImpl<>(List.of(soaps));
    }

    @SuppressWarnings("unused")
    private Oil unusedOil() {
        return Oil.builder().id(1L).name("橄欖油").build();
    }

    @SuppressWarnings("unused")
    private EssentialOil unusedEssentialOil() {
        return EssentialOil.builder().id(1L).name("薰衣草").build();
    }
}