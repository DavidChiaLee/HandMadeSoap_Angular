package com.handsoap.controller;

import com.handsoap.dto.ApiResponse;
import com.handsoap.dto.SoapRequest;
import com.handsoap.dto.SoapResponse;
import com.handsoap.service.SoapService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/soaps")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSoapController {

    private final SoapService soapService;

    public AdminSoapController(SoapService soapService) {
        this.soapService = soapService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SoapResponse>>> getAllSoaps(
            @RequestParam(value = "keyword", required = false) String keyword,
            Pageable pageable) {
        Page<SoapResponse> soaps = (keyword == null || keyword.isBlank())
                ? soapService.getAllSoaps(pageable)
                : soapService.searchSoapsByName(keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(soaps));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SoapResponse>> getSoapById(@PathVariable Long id) {
        SoapResponse soap = soapService.getSoapById(id);
        return ResponseEntity.ok(ApiResponse.success(soap));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SoapResponse>> createSoap(@Valid @RequestBody SoapRequest request) {
        SoapResponse soap = soapService.createSoap(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Soap created successfully", soap));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SoapResponse>> updateSoap(@PathVariable Long id, @Valid @RequestBody SoapRequest request) {
        SoapResponse soap = soapService.updateSoap(id, request);
        return ResponseEntity.ok(ApiResponse.success("Soap updated successfully", soap));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSoap(@PathVariable Long id) {
        soapService.deleteSoap(id);
        return ResponseEntity.ok(ApiResponse.success("Soap deleted successfully", null));
    }

    @PostMapping("/{soapId}/images")
    public ResponseEntity<ApiResponse<SoapResponse>> uploadSoapImage(
            @PathVariable Long soapId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sortOrder", required = false, defaultValue = "0") Integer sortOrder,
            @RequestParam(value = "isPrimary", required = false, defaultValue = "false") Boolean isPrimary) {
        SoapResponse soap = soapService.uploadSoapImage(soapId, file, sortOrder, isPrimary);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Image uploaded successfully", soap));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<ApiResponse<SoapResponse>> deleteSoapImage(
            @PathVariable Long id,
            @PathVariable Long imageId) {
        SoapResponse soap = soapService.deleteSoapImage(id, imageId);
        return ResponseEntity.ok(ApiResponse.success("Image deleted successfully", soap));
    }
}
