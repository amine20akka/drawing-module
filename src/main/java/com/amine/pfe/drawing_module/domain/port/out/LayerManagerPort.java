package com.amine.pfe.drawing_module.domain.port.out;

import java.util.UUID;

import com.amine.pfe.drawing_module.domain.dto.FeatureUpdateRequest;
import com.amine.pfe.drawing_module.domain.dto.FeatureUpdateResult;
import com.amine.pfe.drawing_module.domain.model.LayerSchema;

public interface LayerManagerPort {
    public LayerSchema getLayerSchema(UUID layerId);
    public FeatureUpdateResult updateFeature(UUID layerId, String featureId, FeatureUpdateRequest request);
    public FeatureUpdateResult insertFeature(UUID layerId, FeatureUpdateRequest request);
}
