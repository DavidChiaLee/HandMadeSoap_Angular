package com.handsoap.dto;

import com.handsoap.entity.EssentialOilImage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EssentialOilImageResponse {
    private Long id;
    private Long essentialOilId;
    private String imagePath;
    private Integer sortOrder;
    private Boolean isPrimary;
    private LocalDateTime createdAt;

    public static EssentialOilImageResponse fromEntity(EssentialOilImage image) {
        return EssentialOilImageResponse.builder()
                .id(image.getId())
                .essentialOilId(image.getEssentialOil() != null ? image.getEssentialOil().getId() : null)
                .imagePath(image.getImagePath())
                .sortOrder(image.getSortOrder())
                .isPrimary(image.getIsPrimary())
                .createdAt(image.getCreatedAt())
                .build();
    }
}
