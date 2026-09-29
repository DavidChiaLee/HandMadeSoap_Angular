package com.handsoap.controller;

import com.handsoap.dto.ApiResponse;
import com.handsoap.dto.StatsResponse;
import com.handsoap.repository.ContactRepository;
import com.handsoap.repository.EssentialOilRepository;
import com.handsoap.repository.OilRepository;
import com.handsoap.repository.SoapRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stats")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController {

    private final SoapRepository soapRepository;
    private final OilRepository oilRepository;
    private final EssentialOilRepository essentialOilRepository;
    private final ContactRepository contactRepository;

    public AdminStatsController(SoapRepository soapRepository,
                                OilRepository oilRepository,
                                EssentialOilRepository essentialOilRepository,
                                ContactRepository contactRepository) {
        this.soapRepository = soapRepository;
        this.oilRepository = oilRepository;
        this.essentialOilRepository = essentialOilRepository;
        this.contactRepository = contactRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<StatsResponse>> getStats() {
        StatsResponse stats = StatsResponse.builder()
                .soaps(soapRepository.count())
                .oils(oilRepository.count())
                .essentialOils(essentialOilRepository.count())
                .contacts(contactRepository.count())
                .build();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}