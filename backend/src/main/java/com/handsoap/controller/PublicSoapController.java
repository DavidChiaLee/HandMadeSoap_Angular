package com.handsoap.controller;

import com.handsoap.dto.ApiResponse;
import com.handsoap.dto.SoapResponse;
import com.handsoap.service.SoapService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/soaps")
public class PublicSoapController {

    private final SoapService soapService;

    public PublicSoapController(SoapService soapService) {
        this.soapService = soapService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SoapResponse>>> getAllSoaps(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            Pageable pageable) {
        Page<SoapResponse> soaps = soapService.searchFiltered(categoryId, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(soaps));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SoapResponse>> getSoapById(@PathVariable Long id) {
        SoapResponse soap = soapService.getSoapById(id);
        return ResponseEntity.ok(ApiResponse.success(soap));
    }

    @Deprecated
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<Page<SoapResponse>>> getSoapsByCategory(@PathVariable Long categoryId, Pageable pageable) {
        Page<SoapResponse> soaps = soapService.searchFiltered(categoryId, null, pageable);
        return ResponseEntity.ok(ApiResponse.success(soaps));
    }

    @Deprecated
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<SoapResponse>>> searchSoaps(@RequestParam String keyword, Pageable pageable) {
        Page<SoapResponse> soaps = soapService.searchFiltered(null, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(soaps));
    }

    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<Page<SoapResponse>>> getFeaturedSoaps(Pageable pageable) {
        Page<SoapResponse> soaps = soapService.getFeaturedSoaps(pageable);
        return ResponseEntity.ok(ApiResponse.success(soaps));
    }
}
