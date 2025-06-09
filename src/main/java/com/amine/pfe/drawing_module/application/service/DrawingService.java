package com.amine.pfe.drawing_module.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.amine.pfe.drawing_module.domain.dto.FeatureUpdateRequest;
import com.amine.pfe.drawing_module.domain.dto.FeatureUpdateResult;
import com.amine.pfe.drawing_module.domain.model.LayerSchema;
import com.amine.pfe.drawing_module.domain.port.out.LayerManagerPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DrawingService {
    
    private final LayerManagerPort layerManager;

    public LayerSchema getLayerSchema(UUID layerId) {
        return layerManager.getLayerSchema(layerId);
    }

    public FeatureUpdateResult updateFeature(UUID layerId, String featureId, FeatureUpdateRequest updateRequest) {
        return layerManager.updateFeature(layerId, featureId, updateRequest);
    }

    public FeatureUpdateResult insertFeature(UUID layerId, FeatureUpdateRequest insertRequest) {
        return layerManager.insertFeature(layerId, insertRequest);
    }
}
