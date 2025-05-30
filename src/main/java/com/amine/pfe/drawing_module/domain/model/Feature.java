package com.amine.pfe.drawing_module.domain.model;

import java.util.Map;

import com.amine.pfe.drawing_module.domain.dto.FeatureGeometry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Feature {
    private String id;
    private FeatureGeometry geometry;
    private Map<String, Object> properties;
}