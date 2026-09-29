package com.handsoap.controller;

import com.handsoap.dto.ApiResponse;
import com.handsoap.dto.OilResponse;
import com.handsoap.service.OilService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/oils")
public class PublicOilController {

    private final OilService oilService;

    public PublicOilController(OilService oilService) {
        this.oilService = oilService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OilResponse>>> getAllOils(Pageable pageable) {
        Page<OilResponse> oils = oilService.getAllOils(pageable);
        return ResponseEntity.ok(ApiResponse.success(oils));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OilResponse>> getOilById(@PathVariable Long id) {
        OilResponse oil = oilService.getOilById(id);
        return ResponseEntity.ok(ApiResponse.success(oil));
    }
}
