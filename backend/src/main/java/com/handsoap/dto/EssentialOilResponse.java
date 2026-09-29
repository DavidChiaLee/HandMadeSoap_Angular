package com.handsoap.dto;

import com.handsoap.entity.EssentialOil;
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
public class EssentialOilResponse {
    private Long id;
    private String name;
    private String scentNotes;
    private String benefits;
    private String precautions;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<EssentialOilImageResponse> images;

    public static EssentialOilResponse fromEntity(EssentialOil oil) {
        List<EssentialOilImageResponse> imageResponses = Collections.emptyList();
        if (oil.getImages() != null) {
            imageResponses = oil.getImages().stream()
                    .map(EssentialOilImageResponse::fromEntity)
                    .collect(Collectors.toList());
        }
        return EssentialOilResponse.builder()
                .id(oil.getId())
                .name(oil.getName())
                .scentNotes(oil.getScentNotes())
                .benefits(oil.getBenefits())
                .precautions(oil.getPrecautions())
                .isActive(oil.getIsActive())
                .createdAt(oil.getCreatedAt())
                .updatedAt(oil.getUpdatedAt())
                .images(imageResponses)
                .build();
    }
}
