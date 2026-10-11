package com.handsoap.service;

import com.handsoap.dto.OilRequest;
import com.handsoap.dto.OilResponse;
import com.handsoap.entity.Oil;
import com.handsoap.entity.OilImage;
import com.handsoap.exception.ResourceNotFoundException;
import com.handsoap.repository.KeywordSanitizer;
import com.handsoap.repository.OilRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class OilService {

    private final OilRepository oilRepository;
    private final FileStorageService fileStorageService;

    public OilService(OilRepository oilRepository,
                      FileStorageService fileStorageService) {
        this.oilRepository = oilRepository;
        this.fileStorageService = fileStorageService;
    }

    public Page<OilResponse> getAllOils(Pageable pageable) {
        return oilRepository.findByIsActiveTrue(pageable).map(this::toResponse);
    }

    /**
     * 依名稱關鍵字搜尋；keyword 經 {@link KeywordSanitizer#normalize(String)} 正規化（review L3）。
     *
     * <p>normalize 回 {@code null}（沒有可搜尋內容）時回空結果，不把 null 丟進
     * {@code CONCAT} —— 否則 MySQL 算出 {@code '%%'} 會變成「列出全部油品」。</p>
     */
    public Page<OilResponse> searchOilsByName(String keyword, Pageable pageable) {
        String normalized = KeywordSanitizer.normalize(keyword);
        if (normalized == null) {
            return Page.empty(pageable);
        }
        return oilRepository.searchActiveOilsByName(normalized, pageable).map(this::toResponse);
    }

    public OilResponse getOilById(Long id) {
        Oil oil = oilRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Oil not found with id: " + id));
        return toResponse(oil);
    }

    public OilResponse createOil(OilRequest request) {
        Oil oil = Oil.builder()
                .name(request.getName())
                .origin(request.getOrigin())
                .benefits(request.getBenefits())
                .suitableFor(request.getSuitableFor())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        Oil saved = oilRepository.save(oil);
        return toResponse(saved);
    }

    public OilResponse updateOil(Long id, OilRequest request) {
        Oil oil = oilRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Oil not found with id: " + id));

        oil.setName(request.getName());
        oil.setOrigin(request.getOrigin());
        oil.setBenefits(request.getBenefits());
        oil.setSuitableFor(request.getSuitableFor());
        oil.setIsActive(request.getIsActive() != null ? request.getIsActive() : oil.getIsActive());

        Oil saved = oilRepository.save(oil);
        return toResponse(saved);
    }

    public void deleteOil(Long id) {
        Oil oil = oilRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Oil not found with id: " + id));

        // 先快取檔名再刪 DB（images 為 LAZY）；檔案刪除排程到 commit 之後
        List<String> imagePaths = oil.getImages().stream()
                .map(OilImage::getImagePath)
                .toList();

        oilRepository.delete(oil);
        fileStorageService.deleteFilesAfterCommit(imagePaths);
    }

    /**
     * 刪除單張圖片（review M2）。{@code imageId} 必須屬於 {@code oilId}，否则回 404。
     * DB 列由 {@code orphanRemoval = true} 負責，檔案排程到 commit 之後才刪。
     */
    public OilResponse deleteOilImage(Long oilId, Long imageId) {
        Oil oil = oilRepository.findById(oilId)
                .orElseThrow(() -> new ResourceNotFoundException("Oil not found with id: " + oilId));

        OilImage image = oil.getImages().stream()
                .filter(img -> imageId.equals(img.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Image not found with id: " + imageId + " for oil id: " + oilId));

        String imagePath = image.getImagePath();
        oil.getImages().remove(image);
        oilRepository.save(oil);

        fileStorageService.deleteFilesAfterCommit(List.of(imagePath));
        return toResponse(oil);
    }

    public OilResponse uploadOilImage(Long oilId, MultipartFile file, Integer sortOrder, Boolean isPrimary) {
        Oil oil = oilRepository.findById(oilId)
                .orElseThrow(() -> new ResourceNotFoundException("Oil not found with id: " + oilId));

        String filename = fileStorageService.saveFile(file);

        OilImage image = new OilImage();
        image.setOil(oil);
        image.setImagePath(filename);
        image.setSortOrder(sortOrder != null ? sortOrder : 0);
        image.setIsPrimary(isPrimary != null ? isPrimary : false);

        if (oil.getImages() == null) {
            oil.setImages(new ArrayList<>());
        }
        oil.getImages().add(image);
        Oil saved = oilRepository.save(oil);

        return toResponse(saved);
    }

    private OilResponse toResponse(Oil oil) {
        return OilResponse.fromEntity(oil);
    }
}
