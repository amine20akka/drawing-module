package com.amine.pfe.drawing_module.infrastructure.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.amine.pfe.drawing_module.domain.model.Feature;
import com.amine.pfe.drawing_module.domain.model.LayerCatalog;
import com.amine.pfe.drawing_module.domain.model.LayerSchema;
import com.amine.pfe.drawing_module.domain.port.out.CartographicServerPort;
import com.amine.pfe.drawing_module.domain.port.out.LayerManagerPort;
import com.amine.pfe.drawing_module.domain.port.out.LayerRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.amine.pfe.drawing_module.domain.dto.FeatureGeometry;
import com.amine.pfe.drawing_module.domain.dto.FeatureUpdateRequest;
import com.amine.pfe.drawing_module.domain.dto.FeatureUpdateResult;
import com.amine.pfe.drawing_module.domain.exception.LayerNotFoundException;

@Service
@RequiredArgsConstructor
@Slf4j
public class LayerManagerAdapter implements LayerManagerPort {
    
    private final LayerRepositoryPort catalogRepository;
    private final CartographicServerPort cartographicServerPort;
    private final ObjectMapper objectMapper;

    @Override
    public LayerSchema getLayerSchema(UUID layerId) {
        LayerCatalog catalog = catalogRepository.findLayerCatalogById(layerId)
            .orElseThrow(() -> new LayerNotFoundException("Layer not found: " + layerId));

        return cartographicServerPort.getLayerSchema(
            catalog.workspace(),
            catalog.geoserverLayerName()
        );
    }

    public FeatureUpdateResult updateFeature(UUID layerId, String featureId, FeatureUpdateRequest request) {
        try {
            log.info("Updating feature {} in layer {}", featureId, layerId);

            // 1. Récupérer le catalog de la couche
            LayerCatalog layerCatalog = catalogRepository.findLayerCatalogById(layerId)
                .orElse(null);
            
            if (layerCatalog == null) {
                return FeatureUpdateResult.builder()
                    .success(false)
                    .featureId(featureId)
                    .message("Layer not found: " + layerId)
                    .build();
            }

            log.info("Found layer catalog: {} (GeoServer layer: {})", 
                layerCatalog.name(), layerCatalog.geoserverLayerName());
            
            // 2. Parser la géométrie
            FeatureGeometry geometry = parseGeometry(request.getGeometry());
            if (geometry == null) {
                return FeatureUpdateResult.builder()
                    .success(false)
                    .featureId(featureId)
                    .message("Invalid geometry format")
                    .build();
            }

            Map<String, Object> updatedProperties = new HashMap<>(request.getProperties());

            // 3. Créer le feature à mettre à jour
            Feature feature = Feature.builder()
                .id(featureId)
                .geometry(geometry)
                .properties(updatedProperties)
                .build();

            // 4. Exécuter la mise à jour via WFS-T avec les infos du catalog
            boolean success = cartographicServerPort.updateFeature(layerCatalog, feature);
            
            if (success) {
                log.info("Feature {} updated successfully in layer {}", featureId, layerCatalog.name());
                return FeatureUpdateResult.builder()
                    .success(true)
                    .featureId(featureId)
                    .message("Feature updated successfully")
                    .build();
            } else {
                log.error("Failed to update feature {} in layer {}", featureId, layerCatalog.name());
                return FeatureUpdateResult.builder()
                    .success(false)
                    .featureId(featureId)
                    .message("WFS-T transaction failed")
                    .build();
            }

        } catch (Exception e) {
            log.error("Error updating feature {} in layer {}: {}", featureId, layerId, e.getMessage(), e);
            return FeatureUpdateResult.builder()
                .success(false)
                .featureId(featureId)
                .message("Internal server error: " + e.getMessage())
                .build();
        }
    }

    private FeatureGeometry parseGeometry(Object geometryObj) {
        try {
            if (geometryObj instanceof String) {
                // Si c'est une string JSON, la parser
                String geometryJson = (String) geometryObj;
                return objectMapper.readValue(geometryJson, FeatureGeometry.class);
            } else if (geometryObj instanceof Map) {
                // Si c'est déjà un objet Map, le convertir
                return objectMapper.convertValue(geometryObj, FeatureGeometry.class);
            } else {
                log.error("Unsupported geometry type: {}", geometryObj.getClass());
                return null;
            }
        } catch (Exception e) {
            log.error("Error parsing geometry: {}", e.getMessage());
            return null;
        }
    }
}
