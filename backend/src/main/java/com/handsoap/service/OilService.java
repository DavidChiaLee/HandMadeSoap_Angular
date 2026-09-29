package com.handsoap.service;

import com.handsoap.dto.OilRequest;
import com.handsoap.dto.OilResponse;
import com.handsoap.entity.Oil;
import com.handsoap.entity.OilImage;
import com.handsoap.exception.ResourceNotFoundException;
import com.handsoap.repository.OilRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;

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

    public Page<OilResponse> searchOilsByName(String keyword, Pageable pageable) {
        return oilRepository.searchActiveOilsByName(keyword.trim(), pageable).map(this::toResponse);
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
        oilRepository.delete(oil);
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
