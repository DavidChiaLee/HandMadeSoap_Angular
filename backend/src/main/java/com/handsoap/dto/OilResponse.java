package com.handsoap.dto;

import com.handsoap.entity.Oil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OilResponse {
    private Long id;
    private String name;
    private String origin;
    private String benefits;
    private String suitableFor;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OilImageResponse> images;

    public static OilResponse fromEntity(Oil oil) {
        List<OilImageResponse> imageResponses = Collections.emptyList();
        if (oil.getImages() != null) {
            imageResponses = oil.getImages().stream()
                    .map(OilImageResponse::fromEntity)
                    .collect(Collectors.toList());
        }
        return OilResponse.builder()
                .id(oil.getId())
                .name(oil.getName())
                .origin(oil.getOrigin())
                .benefits(oil.getBenefits())
                .suitableFor(oil.getSuitableFor())
                .isActive(oil.getIsActive())
                .createdAt(oil.getCreatedAt())
                .updatedAt(oil.getUpdatedAt())
                .images(imageResponses)
                .build();
    }
}
