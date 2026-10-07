package com.handsoap.service;

import com.handsoap.dto.EssentialOilRequest;
import com.handsoap.entity.EssentialOil;
import com.handsoap.entity.EssentialOilImage;
import com.handsoap.exception.ResourceNotFoundException;
import com.handsoap.repository.EssentialOilRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class EssentialOilServiceTest {

    @Mock
    private EssentialOilRepository essentialOilRepository;

    @Mock
    private FileStorageService fileStorageService;

    private EssentialOilService service;

    @BeforeEach
    void setUp() {
        service = new EssentialOilService(essentialOilRepository, fileStorageService);
    }

    @Test
    @DisplayName("搜尋時應把去除前後空白的關鍵字傳給 repository，並回傳映射後的分頁結果")
    void shouldSearchByNameWithTrimmedKeyword() {
        Pageable pageable = PageRequest.of(0, 5);
        given(essentialOilRepository.searchActiveEssentialOils("薰衣草", pageable))
                .willReturn(pageOf(essentialOil(1L, "薰衣草精油")));

        Page<?> result = service.searchEssentialOils("  薰衣草  ", pageable);

        then(essentialOilRepository).should().searchActiveEssentialOils("薰衣草", pageable);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("搜尋無結果時應回傳空的分頁")
    void shouldReturnEmptyPageWhenNoMatch() {
        Pageable pageable = PageRequest.of(0, 5);
        given(essentialOilRepository.searchActiveEssentialOils("不存在的精油", pageable))
                .willReturn(new PageImpl<>(List.of()));

        assertThat(service.searchEssentialOils("不存在的精油", pageable).getContent()).isEmpty();
    }

    @Test
    @DisplayName("取得全部資料時應使用 findByIsActiveTrue")
    void shouldReturnAllActiveOils() {
        Pageable pageable = PageRequest.of(0, 5);
        given(essentialOilRepository.findByIsActiveTrue(pageable))
                .willReturn(pageOf(essentialOil(2L, "檸檬精油")));

        assertThat(service.getAllEssentialOils(pageable).getContent()).hasSize(1);
        then(essentialOilRepository).should().findByIsActiveTrue(pageable);
    }

    @Test
    @DisplayName("查無 id 對應資料時應拋出 ResourceNotFoundException")
    void shouldThrowWhenEssentialOilNotFound() {
        given(essentialOilRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEssentialOilById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("查得到 id 對應資料時應回傳 DTO")
    void shouldReturnEssentialOilById() {
        given(essentialOilRepository.findById(1L))
                .willReturn(Optional.of(essentialOil(1L, "茶樹精油")));

        assertThat(service.getEssentialOilById(1L).getName()).isEqualTo("茶樹精油");
    }

    @Test
    @DisplayName("新增時未指定 isActive 應預設為 true")
    void shouldDefaultIsActiveToTrueOnCreate() {
        given(essentialOilRepository.save(any(EssentialOil.class)))
                .willAnswer(inv -> {
                    EssentialOil saved = inv.getArgument(0);
                    saved.setId(10L);
                    return saved;
                });

        EssentialOilRequest request = new EssentialOilRequest();
        request.setName("新精油");
        request.setScentNotes("香調");
        request.setBenefits("功效");
        request.setPrecautions("注意");

        var response = service.createEssentialOil(request);

        ArgumentCaptor<EssentialOil> captor = ArgumentCaptor.forClass(EssentialOil.class);
        then(essentialOilRepository).should().save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isTrue();
        assertThat(response.getName()).isEqualTo("新精油");
    }

    @Test
    @DisplayName("更新不存在的資料應拋出 ResourceNotFoundException")
    void shouldThrowWhenUpdatingMissingOil() {
        given(essentialOilRepository.findById(99L)).willReturn(Optional.empty());

        EssentialOilRequest request = new EssentialOilRequest();
        request.setName("改名");

        assertThatThrownBy(() -> service.updateEssentialOil(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("刪除不存在的資料應拋出 ResourceNotFoundException")
    void shouldThrowWhenDeletingMissingOil() {
        given(essentialOilRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteEssentialOil(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("刪除成功時應呼叫 repository.delete")
    void shouldDeleteExistingOil() {
        EssentialOil oil = essentialOil(3L, "檸檬精油");
        given(essentialOilRepository.findById(3L)).willReturn(Optional.of(oil));

        service.deleteEssentialOil(3L);

        then(essentialOilRepository).should().delete(oil);
    }

    @Test
    @DisplayName("刪除時應把圖檔路徑排程到 commit 後清理（review M2）")
    void shouldScheduleImageFileDeletionAfterCommit() {
        EssentialOil oil = essentialOil(3L, "檸檬精油");
        oil.getImages().add(EssentialOilImage.builder()
                .id(1L).imagePath("eo.png").essentialOil(oil).isPrimary(false).build());
        given(essentialOilRepository.findById(3L)).willReturn(Optional.of(oil));

        service.deleteEssentialOil(3L);

        then(fileStorageService).should().deleteFilesAfterCommit(List.of("eo.png"));
    }

    @Test
    @DisplayName("刪除單張圖片：所屬正確時應移除該圖並排程清檔")
    void shouldDeleteImageBelongingToEssentialOil() {
        EssentialOil oil = essentialOil(1L, "薰衣草精油");
        EssentialOilImage keep = EssentialOilImage.builder()
                .id(7L).imagePath("keep.png").essentialOil(oil).isPrimary(false).build();
        EssentialOilImage remove = EssentialOilImage.builder()
                .id(8L).imagePath("remove.png").essentialOil(oil).isPrimary(false).build();
        oil.getImages().add(keep);
        oil.getImages().add(remove);
        given(essentialOilRepository.findById(1L)).willReturn(Optional.of(oil));
        given(essentialOilRepository.save(any(EssentialOil.class))).willAnswer(inv -> inv.getArgument(0));

        var response = service.deleteEssentialOilImage(1L, 8L);

        assertThat(oil.getImages()).containsExactly(keep);
        assertThat(response.getImages()).hasSize(1);
        then(fileStorageService).should().deleteFilesAfterCommit(List.of("remove.png"));
    }

    @Test
    @DisplayName("刪除單張圖片：imageId 不屬於該精油應拋 ResourceNotFoundException 且不碰檔案")
    void shouldThrowWhenImageNotBelongingToEssentialOil() {
        EssentialOil oil = essentialOil(1L, "薰衣草精油");
        oil.getImages().add(EssentialOilImage.builder()
                .id(7L).imagePath("keep.png").essentialOil(oil).isPrimary(false).build());
        given(essentialOilRepository.findById(1L)).willReturn(Optional.of(oil));

        assertThatThrownBy(() -> service.deleteEssentialOilImage(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        then(fileStorageService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("上傳圖片時找不到精油應拋出 ResourceNotFoundException")
    void shouldThrowWhenUploadingImageForMissingOil() {
        given(essentialOilRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploadEssentialOilImage(99L, mock(MultipartFile.class), 0, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("上傳圖片時應呼叫檔案服務並把檔名掛到精油上")
    void shouldStoreUploadedImage() {
        EssentialOil oil = essentialOil(4L, "薄荷精油");
        given(essentialOilRepository.findById(4L)).willReturn(Optional.of(oil));
        given(fileStorageService.saveFile(any(MultipartFile.class))).willReturn("stored-image.png");
        given(essentialOilRepository.save(any(EssentialOil.class)))
                .willAnswer(inv -> inv.getArgument(0));

        MultipartFile file = mock(MultipartFile.class);
        service.uploadEssentialOilImage(4L, file, 0, true);

        then(fileStorageService).should().saveFile(file);
        assertThat(oil.getImages()).hasSize(1);
        assertThat(oil.getImages().get(0).getImagePath()).isEqualTo("stored-image.png");
        assertThat(oil.getImages().get(0).getIsPrimary()).isTrue();
    }

    private EssentialOil essentialOil(Long id, String name) {
        return EssentialOil.builder()
                .id(id)
                .name(name)
                .scentNotes("香調說明")
                .benefits("功效說明")
                .precautions("注意事項")
                .isActive(true)
                .build();
    }

    private Page<EssentialOil> pageOf(EssentialOil... oils) {
        return new PageImpl<>(List.of(oils));
    }
}