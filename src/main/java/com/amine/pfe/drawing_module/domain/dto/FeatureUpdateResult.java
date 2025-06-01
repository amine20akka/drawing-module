package com.amine.pfe.drawing_module.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FeatureUpdateResult {
    private boolean success;
    private String message;
    private String featureId;
}
