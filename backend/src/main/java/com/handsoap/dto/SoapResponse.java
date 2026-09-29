package com.handsoap.dto;

import com.handsoap.entity.Soap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoapResponse {
    private Long id;
    private CategoryResponse category;
    private String name;
    private String shortDescription;
    private String fullDescription;
    private BigDecimal price;
    private String weight;
    private String dimensions;
    private String usageInstructions;
    private String storageInstructions;
    private String ingredients;
    private String features;
    private List<OilResponse> oils;
    private List<EssentialOilResponse> essentialOils;
    private Boolean isFeatured;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SoapImageResponse> images;

    public static SoapResponse fromEntity(Soap soap) {
        List<SoapImageResponse> imageResponses = Collections.emptyList();
        if (soap.getImages() != null) {
            imageResponses = soap.getImages().stream()
                    .map(SoapImageResponse::fromEntity)
                    .collect(Collectors.toList());
        }
        List<OilResponse> oilResponses = Collections.emptyList();
        if (soap.getOils() != null) {
            oilResponses = soap.getOils().stream()
                    .map(OilResponse::fromEntity)
                    .collect(Collectors.toList());
        }
        List<EssentialOilResponse> essentialOilResponses = Collections.emptyList();
        if (soap.getEssentialOils() != null) {
            essentialOilResponses = soap.getEssentialOils().stream()
                    .map(EssentialOilResponse::fromEntity)
                    .collect(Collectors.toList());
        }
        return SoapResponse.builder()
                .id(soap.getId())
                .category(soap.getCategory() != null ? CategoryResponse.fromEntity(soap.getCategory()) : null)
                .name(soap.getName())
                .shortDescription(soap.getShortDescription())
                .fullDescription(soap.getFullDescription())
                .price(soap.getPrice())
                .weight(soap.getWeight())
                .dimensions(soap.getDimensions())
                .usageInstructions(soap.getUsageInstructions())
                .storageInstructions(soap.getStorageInstructions())
                .ingredients(soap.getIngredients())
                .features(soap.getFeatures())
                .oils(oilResponses)
                .essentialOils(essentialOilResponses)
                .isFeatured(soap.getIsFeatured())
                .isActive(soap.getIsActive())
                .createdAt(soap.getCreatedAt())
                .updatedAt(soap.getUpdatedAt())
                .images(imageResponses)
                .build();
    }
}
