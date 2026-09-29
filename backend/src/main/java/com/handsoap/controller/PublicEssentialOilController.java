package com.handsoap.controller;

import com.handsoap.dto.ApiResponse;
import com.handsoap.dto.EssentialOilResponse;
import com.handsoap.service.EssentialOilService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/essential-oils")
public class PublicEssentialOilController {

    private final EssentialOilService essentialOilService;

    public PublicEssentialOilController(EssentialOilService essentialOilService) {
        this.essentialOilService = essentialOilService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<EssentialOilResponse>>> getAllEssentialOils(Pageable pageable) {
        Page<EssentialOilResponse> oils = essentialOilService.getAllEssentialOils(pageable);
        return ResponseEntity.ok(ApiResponse.success(oils));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EssentialOilResponse>> getEssentialOilById(@PathVariable Long id) {
        EssentialOilResponse oil = essentialOilService.getEssentialOilById(id);
        return ResponseEntity.ok(ApiResponse.success(oil));
    }
}
