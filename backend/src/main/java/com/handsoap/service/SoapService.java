package com.handsoap.service;

import com.handsoap.dto.SoapRequest;
import com.handsoap.dto.SoapResponse;
import com.handsoap.entity.Category;
import com.handsoap.entity.EssentialOil;
import com.handsoap.entity.Oil;
import com.handsoap.entity.Soap;
import com.handsoap.entity.SoapImage;
import com.handsoap.exception.BadRequestException;
import com.handsoap.exception.ResourceNotFoundException;
import com.handsoap.repository.CategoryRepository;
import com.handsoap.repository.EssentialOilRepository;
import com.handsoap.repository.OilRepository;
import com.handsoap.repository.SoapRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@Transactional
public class SoapService {

    private final SoapRepository soapRepository;
    private final CategoryRepository categoryRepository;
    private final OilRepository oilRepository;
    private final EssentialOilRepository essentialOilRepository;
    private final FileStorageService fileStorageService;

    public SoapService(SoapRepository soapRepository,
                       CategoryRepository categoryRepository,
                       OilRepository oilRepository,
                       EssentialOilRepository essentialOilRepository,
                       FileStorageService fileStorageService) {
        this.soapRepository = soapRepository;
        this.categoryRepository = categoryRepository;
        this.oilRepository = oilRepository;
        this.essentialOilRepository = essentialOilRepository;
        this.fileStorageService = fileStorageService;
    }

    public Page<SoapResponse> getAllSoaps(Pageable pageable) {
        return soapRepository.findByIsActiveTrue(pageable).map(this::toResponse);
    }

    public SoapResponse getSoapById(Long id) {
        Soap soap = soapRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Soap not found with id: " + id));
        return toResponse(soap);
    }

    public Page<SoapResponse> getSoapsByCategory(Long categoryId, Pageable pageable) {
        return soapRepository.findByCategoryIdAndIsActiveTrue(categoryId, pageable).map(this::toResponse);
    }

    public Page<SoapResponse> searchSoaps(String keyword, Pageable pageable) {
        return soapRepository.searchActiveSoaps(keyword, pageable).map(this::toResponse);
    }

    public Page<SoapResponse> searchSoapsByName(String keyword, Pageable pageable) {
        return soapRepository.searchActiveSoapsByName(keyword.trim(), pageable).map(this::toResponse);
    }

    public Page<SoapResponse> getFeaturedSoaps(Pageable pageable) {
        return soapRepository.findByIsFeaturedTrueAndIsActiveTrue(pageable).map(this::toResponse);
    }

    public SoapResponse createSoap(SoapRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BadRequestException("Invalid category ID"));

        Soap soap = Soap.builder()
                .category(category)
                .name(request.getName())
                .shortDescription(request.getShortDescription())
                .fullDescription(request.getFullDescription())
                .price(request.getPrice() != null ? request.getPrice() : BigDecimal.ZERO)
                .weight(request.getWeight())
                .dimensions(request.getDimensions())
                .usageInstructions(request.getUsageInstructions())
                .storageInstructions(request.getStorageInstructions())
                .ingredients(request.getIngredients())
                .features(request.getFeatures())
                .isFeatured(request.getIsFeatured() != null ? request.getIsFeatured() : false)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .oils(new HashSet<>(resolveOils(request.getOilIds())))
                .essentialOils(new HashSet<>(resolveEssentialOils(request.getEssentialOilIds())))
                .build();

        Soap saved = soapRepository.save(soap);
        return toResponse(saved);
    }

    public SoapResponse updateSoap(Long id, SoapRequest request) {
        Soap soap = soapRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Soap not found with id: " + id));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BadRequestException("Invalid category ID"));

        soap.setCategory(category);
        soap.setName(request.getName());
        soap.setShortDescription(request.getShortDescription());
        soap.setFullDescription(request.getFullDescription());
        soap.setPrice(request.getPrice() != null ? request.getPrice() : BigDecimal.ZERO);
        soap.setWeight(request.getWeight());
        soap.setDimensions(request.getDimensions());
        soap.setUsageInstructions(request.getUsageInstructions());
        soap.setStorageInstructions(request.getStorageInstructions());
        soap.setIngredients(request.getIngredients());
        soap.setFeatures(request.getFeatures());
        soap.setOils(new HashSet<>(resolveOils(request.getOilIds())));
        soap.setEssentialOils(new HashSet<>(resolveEssentialOils(request.getEssentialOilIds())));
        soap.setIsFeatured(request.getIsFeatured() != null ? request.getIsFeatured() : soap.getIsFeatured());
        soap.setIsActive(request.getIsActive() != null ? request.getIsActive() : soap.getIsActive());

        Soap saved = soapRepository.save(soap);
        return toResponse(saved);
    }

    public void deleteSoap(Long id) {
        Soap soap = soapRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Soap not found with id: " + id));

        // 先快取檔名再刪 DB：images 是 LAZY，transaction 結束後就存取不到；
        // 檔案刪除排程到 commit 之後（見 FileStorageService.deleteFilesAfterCommit 的說明）。
        List<String> imagePaths = soap.getImages().stream()
                .map(SoapImage::getImagePath)
                .toList();

        soapRepository.delete(soap);
        fileStorageService.deleteFilesAfterCommit(imagePaths);
    }

    /**
     * 刪除單張圖片（review M2：補上傳後無法移除個別圖片的缺口）。
     *
     * <p>{@code imageId} 必須屬於 {@code soapId}，否则回 404 —— 否則拿別人的
     * {@code imageId} 配上自己的 {@code soapId} 可能誤刪他人物件的圖片。
     * DB 列的移除由 {@code orphanRemoval = true} 負責（collection remove 即可），
     * 檔案一樣排程到 commit 之後才刪。
     */
    public SoapResponse deleteSoapImage(Long soapId, Long imageId) {
        Soap soap = soapRepository.findById(soapId)
                .orElseThrow(() -> new ResourceNotFoundException("Soap not found with id: " + soapId));

        SoapImage image = soap.getImages().stream()
                .filter(img -> imageId.equals(img.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Image not found with id: " + imageId + " for soap id: " + soapId));

        String imagePath = image.getImagePath();
        soap.getImages().remove(image); // orphanRemoval=true → commit 時刪列
        soapRepository.save(soap);

        fileStorageService.deleteFilesAfterCommit(List.of(imagePath));
        return toResponse(soap);
    }

    private List<Oil> resolveOils(List<Long> oilIds) {
        if (oilIds == null || oilIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Oil> oils = oilRepository.findAllById(oilIds);
        if (oils.size() != new HashSet<>(oilIds).size()) {
            throw new BadRequestException("Invalid oil ID");
        }
        return oils;
    }

    private List<EssentialOil> resolveEssentialOils(List<Long> essentialOilIds) {
        if (essentialOilIds == null || essentialOilIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<EssentialOil> oils = essentialOilRepository.findAllById(essentialOilIds);
        if (oils.size() != new HashSet<>(essentialOilIds).size()) {
            throw new BadRequestException("Invalid essential oil ID");
        }
        return oils;
    }

    public SoapResponse uploadSoapImage(Long soapId, MultipartFile file, Integer sortOrder, Boolean isPrimary) {
        Soap soap = soapRepository.findById(soapId)
                .orElseThrow(() -> new ResourceNotFoundException("Soap not found with id: " + soapId));

        String filename = fileStorageService.saveFile(file);

        com.handsoap.entity.SoapImage image = new com.handsoap.entity.SoapImage();
        image.setSoap(soap);
        image.setImagePath(filename);
        image.setSortOrder(sortOrder != null ? sortOrder : 0);
        image.setIsPrimary(isPrimary != null ? isPrimary : false);

        if (soap.getImages() == null) {
            soap.setImages(new java.util.ArrayList<>());
        }
        soap.getImages().add(image);
        soapRepository.save(soap);

        return toResponse(soap);
    }

    private SoapResponse toResponse(Soap soap) {
        return SoapResponse.fromEntity(soap);
    }
}
