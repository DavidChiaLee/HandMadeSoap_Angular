package com.handsoap.service;

import com.handsoap.dto.EssentialOilRequest;
import com.handsoap.dto.EssentialOilResponse;
import com.handsoap.entity.EssentialOil;
import com.handsoap.entity.EssentialOilImage;
import com.handsoap.exception.ResourceNotFoundException;
import com.handsoap.repository.EssentialOilRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class EssentialOilService {

    private final EssentialOilRepository essentialOilRepository;
    private final FileStorageService fileStorageService;

    public EssentialOilService(EssentialOilRepository essentialOilRepository, FileStorageService fileStorageService) {
        this.essentialOilRepository = essentialOilRepository;
        this.fileStorageService = fileStorageService;
    }

    public Page<EssentialOilResponse> getAllEssentialOils(Pageable pageable) {
        return essentialOilRepository.findByIsActiveTrue(pageable).map(EssentialOilResponse::fromEntity);
    }

    public Page<EssentialOilResponse> searchEssentialOils(String keyword, Pageable pageable) {
        return essentialOilRepository.searchActiveEssentialOils(keyword.trim(), pageable)
                .map(EssentialOilResponse::fromEntity);
    }

    public EssentialOilResponse getEssentialOilById(Long id) {
        EssentialOil essentialOil = essentialOilRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Essential oil not found with id: " + id));
        return EssentialOilResponse.fromEntity(essentialOil);
    }

    public EssentialOilResponse createEssentialOil(EssentialOilRequest request) {
        EssentialOil essentialOil = EssentialOil.builder()
                .name(request.getName())
                .scentNotes(request.getScentNotes())
                .benefits(request.getBenefits())
                .precautions(request.getPrecautions())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        EssentialOil saved = essentialOilRepository.save(essentialOil);
        return EssentialOilResponse.fromEntity(saved);
    }

    public EssentialOilResponse updateEssentialOil(Long id, EssentialOilRequest request) {
        EssentialOil essentialOil = essentialOilRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Essential oil not found with id: " + id));

        essentialOil.setName(request.getName());
        essentialOil.setScentNotes(request.getScentNotes());
        essentialOil.setBenefits(request.getBenefits());
        essentialOil.setPrecautions(request.getPrecautions());
        essentialOil.setIsActive(request.getIsActive() != null ? request.getIsActive() : essentialOil.getIsActive());

        EssentialOil saved = essentialOilRepository.save(essentialOil);
        return EssentialOilResponse.fromEntity(saved);
    }

    public void deleteEssentialOil(Long id) {
        EssentialOil essentialOil = essentialOilRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Essential oil not found with id: " + id));

        // 先快取檔名再刪 DB（images 為 LAZY）；檔案刪除排程到 commit 之後
        List<String> imagePaths = essentialOil.getImages().stream()
                .map(EssentialOilImage::getImagePath)
                .toList();

        essentialOilRepository.delete(essentialOil);
        fileStorageService.deleteFilesAfterCommit(imagePaths);
    }

    /**
     * 刪除單張圖片（review M2）。{@code imageId} 必須屬於 {@code essentialOilId}，否则回 404。
     * DB 列由 {@code orphanRemoval = true} 負責，檔案排程到 commit 之後才刪。
     */
    public EssentialOilResponse deleteEssentialOilImage(Long essentialOilId, Long imageId) {
        EssentialOil essentialOil = essentialOilRepository.findById(essentialOilId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Essential oil not found with id: " + essentialOilId));

        EssentialOilImage image = essentialOil.getImages().stream()
                .filter(img -> imageId.equals(img.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Image not found with id: " + imageId + " for essential oil id: " + essentialOilId));

        String imagePath = image.getImagePath();
        essentialOil.getImages().remove(image);
        essentialOilRepository.save(essentialOil);

        fileStorageService.deleteFilesAfterCommit(List.of(imagePath));
        return EssentialOilResponse.fromEntity(essentialOil);
    }

    public EssentialOilResponse uploadEssentialOilImage(Long essentialOilId, MultipartFile file, Integer sortOrder, Boolean isPrimary) {
        EssentialOil essentialOil = essentialOilRepository.findById(essentialOilId)
                .orElseThrow(() -> new ResourceNotFoundException("Essential oil not found with id: " + essentialOilId));

        String filename = fileStorageService.saveFile(file);

        EssentialOilImage image = new EssentialOilImage();
        image.setEssentialOil(essentialOil);
        image.setImagePath(filename);
        image.setSortOrder(sortOrder != null ? sortOrder : 0);
        image.setIsPrimary(isPrimary != null ? isPrimary : false);

        if (essentialOil.getImages() == null) {
            essentialOil.setImages(new ArrayList<>());
        }
        essentialOil.getImages().add(image);
        EssentialOil saved = essentialOilRepository.save(essentialOil);

        return EssentialOilResponse.fromEntity(saved);
    }
}
