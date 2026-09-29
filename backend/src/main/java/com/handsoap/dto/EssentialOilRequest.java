package com.handsoap.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EssentialOilRequest {
    private String name;
    private String scentNotes;
    private String benefits;
    private String precautions;
    private Boolean isActive;
}
