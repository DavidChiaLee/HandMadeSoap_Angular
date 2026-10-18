package com.handsoap.controller;

import com.handsoap.dto.ApiResponse;
import com.handsoap.dto.EssentialOilRequest;
import com.handsoap.dto.EssentialOilResponse;
import com.handsoap.service.EssentialOilService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/essential-oils")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEssentialOilController {

    private final EssentialOilService essentialOilService;

    public AdminEssentialOilController(EssentialOilService essentialOilService) {
        this.essentialOilService = essentialOilService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<EssentialOilResponse>>> getAllEssentialOils(
            @RequestParam(value = "keyword", required = false) String keyword,
            Pageable pageable) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword;
        Page<EssentialOilResponse> oils = (normalizedKeyword == null)
                ? essentialOilService.getAllEssentialOils(pageable)
                : essentialOilService.searchEssentialOilsByName(normalizedKeyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(oils));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EssentialOilResponse>> getEssentialOilById(@PathVariable Long id) {
        EssentialOilResponse oil = essentialOilService.getEssentialOilById(id);
        return ResponseEntity.ok(ApiResponse.success(oil));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EssentialOilResponse>> createEssentialOil(@Valid @RequestBody EssentialOilRequest request) {
        EssentialOilResponse oil = essentialOilService.createEssentialOil(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Essential oil created successfully", oil));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EssentialOilResponse>> updateEssentialOil(@PathVariable Long id, @Valid @RequestBody EssentialOilRequest request) {
        EssentialOilResponse oil = essentialOilService.updateEssentialOil(id, request);
        return ResponseEntity.ok(ApiResponse.success("Essential oil updated successfully", oil));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEssentialOil(@PathVariable Long id) {
        essentialOilService.deleteEssentialOil(id);
        return ResponseEntity.ok(ApiResponse.success("Essential oil deleted successfully", null));
    }

    @PostMapping("/{essentialOilId}/images")
    public ResponseEntity<ApiResponse<EssentialOilResponse>> uploadEssentialOilImage(
            @PathVariable Long essentialOilId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sortOrder", required = false, defaultValue = "0") Integer sortOrder,
            @RequestParam(value = "isPrimary", required = false, defaultValue = "false") Boolean isPrimary) {
        EssentialOilResponse oil = essentialOilService.uploadEssentialOilImage(essentialOilId, file, sortOrder, isPrimary);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Image uploaded successfully", oil));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<ApiResponse<EssentialOilResponse>> deleteEssentialOilImage(
            @PathVariable Long id,
            @PathVariable Long imageId) {
        EssentialOilResponse oil = essentialOilService.deleteEssentialOilImage(id, imageId);
        return ResponseEntity.ok(ApiResponse.success("Image deleted successfully", oil));
    }
}
