package com.amine.pfe.drawing_module.domain.port.in;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.amine.pfe.drawing_module.domain.dto.FeatureUpdateRequest;
import com.amine.pfe.drawing_module.domain.dto.FeatureUpdateResult;
import com.amine.pfe.drawing_module.domain.model.LayerSchema;

public interface DrawingWebPort {
    LayerSchema getLayerSchema(UUID layerId);
    public ResponseEntity<FeatureUpdateResult> updateFeature(UUID layerId, String featureId, FeatureUpdateRequest updateRequest);
    public ResponseEntity<FeatureUpdateResult> insertFeature(UUID layerId, FeatureUpdateRequest createRequest);
}