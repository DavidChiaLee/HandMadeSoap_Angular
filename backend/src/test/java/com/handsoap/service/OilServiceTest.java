package com.handsoap.service;

import com.handsoap.dto.OilRequest;
import com.handsoap.entity.Oil;
import com.handsoap.entity.OilImage;
import com.handsoap.exception.ResourceNotFoundException;
import com.handsoap.repository.OilRepository;
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

@ExtendWith(MockitoExtension.class)
class OilServiceTest {

    @Mock
    private OilRepository oilRepository;

    @Mock
    private FileStorageService fileStorageService;

    private OilService service;

    @BeforeEach
    void setUp() {
        service = new OilService(oilRepository, fileStorageService);
    }

    @Test
    @DisplayName("搜尋時應把去除前後空白的關鍵字傳給 repository")
    void shouldSearchByNameWithTrimmedKeyword() {
        Pageable pageable = PageRequest.of(0, 5);
        given(oilRepository.searchActiveOilsByName("橄欖", pageable))
                .willReturn(new PageImpl<>(List.of(oil(1L, "橄欖油"))));

        var result = service.searchOilsByName("  橄欖  ", pageable);

        then(oilRepository).should().searchActiveOilsByName("橄欖", pageable);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("搜尋無結果時應回傳空的分頁")
    void shouldReturnEmptyPageWhenNoMatch() {
        Pageable pageable = PageRequest.of(0, 5);
        given(oilRepository.searchActiveOilsByName("zzz", pageable))
                .willReturn(new PageImpl<>(List.of()));

        assertThat(service.searchOilsByName("zzz", pageable).getContent()).isEmpty();
    }

    @Test
    @DisplayName("取得全部資料時應使用 findByIsActiveTrue")
    void shouldReturnAllActiveOils() {
        Pageable pageable = PageRequest.of(0, 5);
        given(oilRepository.findByIsActiveTrue(pageable))
                .willReturn(new PageImpl<>(List.of(oil(2L, "椰子油"))));

        assertThat(service.getAllOils(pageable).getContent()).hasSize(1);
        then(oilRepository).should().findByIsActiveTrue(pageable);
    }

    @Test
    @DisplayName("查無 id 對應資料時應拋出 ResourceNotFoundException")
    void shouldThrowWhenOilNotFound() {
        given(oilRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOilById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("查得到資料時應回傳 DTO")
    void shouldReturnOilById() {
        given(oilRepository.findById(1L)).willReturn(Optional.of(oil(1L, "橄欖油")));

        assertThat(service.getOilById(1L).getName()).isEqualTo("橄欖油");
    }

    @Test
    @DisplayName("新增時未指定 isActive 應預設為 true")
    void shouldDefaultIsActiveToTrueOnCreate() {
        given(oilRepository.save(any(Oil.class))).willAnswer(inv -> {
            Oil saved = inv.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        OilRequest request = new OilRequest();
        request.setName("新油品");
        request.setOrigin("台灣");
        request.setBenefits("護膚");
        request.setSuitableFor("乾性肌");

        var response = service.createOil(request);

        assertThat(response.getName()).isEqualTo("新油品");
        assertThat(response.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("更新時 isActive 為 null 應保留原本的值")
    void shouldKeepExistingIsActiveWhenUpdateOmitsIt() {
        Oil existing = oil(5L, "苦茶油");
        existing.setIsActive(false);
        given(oilRepository.findById(5L)).willReturn(Optional.of(existing));
        given(oilRepository.save(any(Oil.class))).willAnswer(inv -> inv.getArgument(0));

        OilRequest request = new OilRequest();
        request.setName("苦茶油改");

        service.updateOil(5L, request);

        assertThat(existing.getIsActive()).isFalse();
        assertThat(existing.getName()).isEqualTo("苦茶油改");
    }

    @Test
    @DisplayName("刪除不存在的資料應拋出 ResourceNotFoundException")
    void shouldThrowWhenDeletingMissingOil() {
        given(oilRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteOil(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("刪除成功時應呼叫 repository.delete")
    void shouldDeleteExistingOil() {
        Oil existing = oil(3L, "米糠油");
        given(oilRepository.findById(3L)).willReturn(Optional.of(existing));

        service.deleteOil(3L);

        then(oilRepository).should().delete(existing);
    }

    @Test
    @DisplayName("刪除時應把圖檔路徑排程到 commit 後清理（review M2）")
    void shouldScheduleImageFileDeletionAfterCommit() {
        Oil existing = oil(3L, "米糠油");
        existing.getImages().add(OilImage.builder().id(1L).imagePath("oil.png").oil(existing).isPrimary(false).build());
        given(oilRepository.findById(3L)).willReturn(Optional.of(existing));

        service.deleteOil(3L);

        then(fileStorageService).should().deleteFilesAfterCommit(List.of("oil.png"));
    }

    @Test
    @DisplayName("刪除單張圖片：所屬正確時應移除該圖並排程清檔")
    void shouldDeleteImageBelongingToOil() {
        Oil existing = oil(1L, "橄欖油");
        OilImage keep = OilImage.builder().id(7L).imagePath("keep.png").oil(existing).isPrimary(false).build();
        OilImage remove = OilImage.builder().id(8L).imagePath("remove.png").oil(existing).isPrimary(false).build();
        existing.getImages().add(keep);
        existing.getImages().add(remove);
        given(oilRepository.findById(1L)).willReturn(Optional.of(existing));
        given(oilRepository.save(any(Oil.class))).willAnswer(inv -> inv.getArgument(0));

        var response = service.deleteOilImage(1L, 8L);

        assertThat(existing.getImages()).containsExactly(keep);
        assertThat(response.getImages()).hasSize(1);
        then(fileStorageService).should().deleteFilesAfterCommit(List.of("remove.png"));
    }

    @Test
    @DisplayName("刪除單張圖片：imageId 不屬於該 oil 應拋 ResourceNotFoundException 且不碰檔案")
    void shouldThrowWhenImageNotBelongingToOil() {
        Oil existing = oil(1L, "橄欖油");
        existing.getImages().add(OilImage.builder().id(7L).imagePath("keep.png").oil(existing).isPrimary(false).build());
        given(oilRepository.findById(1L)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.deleteOilImage(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        then(fileStorageService).shouldHaveNoInteractions();
    }

    private Oil oil(Long id, String name) {
        return Oil.builder()
                .id(id)
                .name(name)
                .origin("台灣")
                .benefits("護膚")
                .suitableFor("乾性肌")
                .isActive(true)
                .build();
    }
}