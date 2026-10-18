package com.handsoap.controller;

import com.handsoap.dto.ApiResponse;
import com.handsoap.dto.OilRequest;
import com.handsoap.dto.OilResponse;
import com.handsoap.service.OilService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/oils")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOilController {

    private final OilService oilService;

    public AdminOilController(OilService oilService) {
        this.oilService = oilService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OilResponse>>> getAllOils(
            @RequestParam(value = "keyword", required = false) String keyword,
            Pageable pageable) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword;
        Page<OilResponse> oils = (normalizedKeyword == null)
                ? oilService.getAllOils(pageable)
                : oilService.searchOilsByName(normalizedKeyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(oils));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OilResponse>> getOilById(@PathVariable Long id) {
        OilResponse oil = oilService.getOilById(id);
        return ResponseEntity.ok(ApiResponse.success(oil));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OilResponse>> createOil(@Valid @RequestBody OilRequest request) {
        OilResponse oil = oilService.createOil(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Oil created successfully", oil));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OilResponse>> updateOil(@PathVariable Long id, @Valid @RequestBody OilRequest request) {
        OilResponse oil = oilService.updateOil(id, request);
        return ResponseEntity.ok(ApiResponse.success("Oil updated successfully", oil));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteOil(@PathVariable Long id) {
        oilService.deleteOil(id);
        return ResponseEntity.ok(ApiResponse.success("Oil deleted successfully", null));
    }

    @PostMapping("/{oilId}/images")
    public ResponseEntity<ApiResponse<OilResponse>> uploadOilImage(
            @PathVariable Long oilId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sortOrder", required = false, defaultValue = "0") Integer sortOrder,
            @RequestParam(value = "isPrimary", required = false, defaultValue = "false") Boolean isPrimary) {
        OilResponse oil = oilService.uploadOilImage(oilId, file, sortOrder, isPrimary);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Image uploaded successfully", oil));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<ApiResponse<OilResponse>> deleteOilImage(
            @PathVariable Long id,
            @PathVariable Long imageId) {
        OilResponse oil = oilService.deleteOilImage(id, imageId);
        return ResponseEntity.ok(ApiResponse.success("Image deleted successfully", oil));
    }
}
