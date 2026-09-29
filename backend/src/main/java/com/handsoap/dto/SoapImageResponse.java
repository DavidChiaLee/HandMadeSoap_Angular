package com.handsoap.dto;

import com.handsoap.entity.SoapImage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoapImageResponse {
    private Long id;
    private Long soapId;
    private String imagePath;
    private Integer sortOrder;
    private Boolean isPrimary;
    private LocalDateTime createdAt;

    public static SoapImageResponse fromEntity(SoapImage image) {
        return SoapImageResponse.builder()
                .id(image.getId())
                .soapId(image.getSoap() != null ? image.getSoap().getId() : null)
                .imagePath(image.getImagePath())
                .sortOrder(image.getSortOrder())
                .isPrimary(image.getIsPrimary())
                .createdAt(image.getCreatedAt())
                .build();
    }
}
