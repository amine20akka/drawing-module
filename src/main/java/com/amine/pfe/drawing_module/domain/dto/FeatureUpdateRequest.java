package com.amine.pfe.drawing_module.domain.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FeatureUpdateRequest {
    private String geometry;
    private Map<String, Object> properties;
}
