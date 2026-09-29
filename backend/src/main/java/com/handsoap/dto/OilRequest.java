package com.handsoap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OilRequest {
    @NotBlank(message = "名稱不可為空")
    @Size(max = 200, message = "名稱最多 200 字")
    private String name;

    @Size(max = 200, message = "來源最多 200 字")
    private String origin;

    private String benefits;
    private String suitableFor;
    private Boolean isActive;
}
