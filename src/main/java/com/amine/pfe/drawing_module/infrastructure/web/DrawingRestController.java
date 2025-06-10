package com.amine.pfe.drawing_module.infrastructure.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amine.pfe.drawing_module.domain.dto.FeatureUpdateRequest;
import com.amine.pfe.drawing_module.domain.dto.FeatureUpdateResult;
import com.amine.pfe.drawing_module.domain.model.LayerSchema;
import com.amine.pfe.drawing_module.domain.port.in.DrawingWebPort;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/drawing/layers")
@RequiredArgsConstructor
public class DrawingRestController {

    private final DrawingWebPort drawingWebPort;

    @GetMapping(value = "/{layerId}/schema")
    public LayerSchema getLayerSchema(@PathVariable UUID layerId) {
        return drawingWebPort.getLayerSchema(layerId);
    }

    @PostMapping(value = "/{layerId}/features", consumes = "application/json; charset=UTF-8",
                                                            produces = "application/json; charset=UTF-8")
    public ResponseEntity<FeatureUpdateResult> insertFeature(
        @PathVariable UUID layerId,
        @RequestBody FeatureUpdateRequest insertRequest) {
        return drawingWebPort.insertFeature(layerId, insertRequest);
    }

    @PutMapping(value = "/{layerId}/features/{featureId}", consumes = "application/json; charset=UTF-8",
                                                            produces = "application/json; charset=UTF-8")
    public ResponseEntity<FeatureUpdateResult> updateFeature(
            @PathVariable UUID layerId,
            @PathVariable String featureId,
            @RequestBody FeatureUpdateRequest updateRequest) {
        return drawingWebPort.updateFeature(layerId, featureId, updateRequest);
    }

    @DeleteMapping(value = "/{layerId}/features/{featureId}", produces = "application/json; charset=UTF-8")
    public ResponseEntity<FeatureUpdateResult> deleteFeature(
        @PathVariable UUID layerId,
        @PathVariable String featureId) {
        return drawingWebPort.deleteFeature(layerId, featureId);
    }
}