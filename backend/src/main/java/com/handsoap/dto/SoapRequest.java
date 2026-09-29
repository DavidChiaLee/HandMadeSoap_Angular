package com.handsoap.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoapRequest {
    private Long categoryId;
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
    private List<Long> oilIds;
    private List<Long> essentialOilIds;
    private Boolean isFeatured;
    private Boolean isActive;
}
