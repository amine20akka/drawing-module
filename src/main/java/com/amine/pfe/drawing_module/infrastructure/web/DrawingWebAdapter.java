package com.amine.pfe.drawing_module.infrastructure.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import com.amine.pfe.drawing_module.application.service.DrawingService;
import com.amine.pfe.drawing_module.domain.dto.FeatureUpdateRequest;
import com.amine.pfe.drawing_module.domain.dto.FeatureUpdateResult;
import com.amine.pfe.drawing_module.domain.model.LayerSchema;
import com.amine.pfe.drawing_module.domain.port.in.DrawingWebPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DrawingWebAdapter implements DrawingWebPort {

    private final DrawingService drawingService;

    @Override
    public LayerSchema getLayerSchema(UUID layerId) {
        return drawingService.getLayerSchema(layerId);
    }

    @Override
    public ResponseEntity<FeatureUpdateResult> insertFeature(UUID layerId, FeatureUpdateRequest insertRequest) {
        
        log.info("Received create request in layer {}", layerId);
        log.debug("Create request: {}", insertRequest);
        
        try {
            FeatureUpdateResult result = drawingService.insertFeature(layerId, insertRequest);
            
            if (result.isSuccess()) {
                log.info("Feature {} created successfully", result.getFeatureId());
                return ResponseEntity.ok(result);
            } else {
                log.warn("Feature creation failed: {}", result.getMessage());
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Unexpected error creating a new feature : {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @Override
    public ResponseEntity<FeatureUpdateResult> updateFeature(UUID layerId, String featureId, FeatureUpdateRequest updateRequest) {
        
        log.info("Received update request for feature {} in layer {}", featureId, layerId);
        log.debug("Update request: {}", updateRequest);
        
        try {
            FeatureUpdateResult result = drawingService.updateFeature(layerId, featureId, updateRequest);
            
            if (result.isSuccess()) {
                log.info("Feature {} updated successfully", featureId);
                return ResponseEntity.ok(result);
            } else {
                log.warn("Feature update failed: {}", result.getMessage());
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Unexpected error updating feature {}: {}", featureId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(null);
        }
    }
}