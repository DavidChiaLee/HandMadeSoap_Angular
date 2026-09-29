package com.handsoap.dto;

import com.handsoap.entity.OilImage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OilImageResponse {
    private Long id;
    private Long oilId;
    private String imagePath;
    private Integer sortOrder;
    private Boolean isPrimary;
    private LocalDateTime createdAt;

    public static OilImageResponse fromEntity(OilImage image) {
        return OilImageResponse.builder()
                .id(image.getId())
                .oilId(image.getOil() != null ? image.getOil().getId() : null)
                .imagePath(image.getImagePath())
                .sortOrder(image.getSortOrder())
                .isPrimary(image.getIsPrimary())
                .createdAt(image.getCreatedAt())
                .build();
    }
}
