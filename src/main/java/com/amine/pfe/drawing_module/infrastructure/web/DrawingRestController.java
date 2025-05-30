package com.amine.pfe.drawing_module.infrastructure.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/{layerId}/schema")
    public LayerSchema getLayerSchema(@PathVariable UUID layerId) {
        return drawingWebPort.getLayerSchema(layerId);
    }

    @PutMapping("/{layerId}/{featureId}/update")
    public ResponseEntity<FeatureUpdateResult> updateFeature(
            @PathVariable UUID layerId,
            @PathVariable String featureId,
            @RequestBody FeatureUpdateRequest updateRequest) {
        return drawingWebPort.updateFeature(layerId, featureId, updateRequest);
    }
}