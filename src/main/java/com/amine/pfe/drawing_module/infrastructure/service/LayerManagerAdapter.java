package com.amine.pfe.drawing_module.infrastructure.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.amine.pfe.drawing_module.domain.model.Feature;
import com.amine.pfe.drawing_module.domain.model.FeatureGeometry;
import com.amine.pfe.drawing_module.domain.model.LayerCatalog;
import com.amine.pfe.drawing_module.domain.model.LayerSchema;
import com.amine.pfe.drawing_module.domain.port.out.CartographicServerPort;
import com.amine.pfe.drawing_module.domain.port.out.LayerManagerPort;
import com.amine.pfe.drawing_module.domain.port.out.LayerRepositoryPort;
import com.amine.pfe.drawing_module.domain.util.MappingUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
                catalog.geoserverLayerName());
    }

    @Override
    public FeatureUpdateResult insertFeature(UUID layerId, FeatureUpdateRequest request) {
        try {
            log.info("Creating feature with request {} in layer {}", request, layerId);

            // 1. Récupérer le catalog de la couche
            LayerCatalog layerCatalog = catalogRepository.findLayerCatalogById(layerId)
                    .orElse(null);

            if (layerCatalog == null) {
                return FeatureUpdateResult.builder()
                        .success(false)
                        .message("Layer not found: " + layerId)
                        .build();
            }

            // 2. Parser la géométrie
            FeatureGeometry geometry = parseGeometry(request.getGeometry());
            log.info("Parsed geometry: {}", geometry);
            if (geometry == null) {
                return FeatureUpdateResult.builder()
                        .success(false)
                        .message("Invalid geometry format")
                        .build();
            }

            // 3. Obtenir le schéma directement via GeoServer
            LayerSchema layerSchema = cartographicServerPort.getLayerSchema(
                    layerCatalog.workspace(), layerCatalog.geoserverLayerName());

            Map<String, String> attributeTypes = layerSchema.attributes().stream()
                    .collect(Collectors.toMap(LayerSchema.Attribute::label, LayerSchema.Attribute::javaType));

            // 4. Convertir les propriétés en fonction du type attendu
            Map<String, Object> properties = new HashMap<>();
            for (Map.Entry<String, Object> entry : request.getProperties().entrySet()) {
                String key = entry.getKey();
                Object rawValue = entry.getValue();
                String expectedType = attributeTypes.get(key);

                Object convertedValue = MappingUtils.convertValueToExpectedType(rawValue, expectedType);
                properties.put(key, convertedValue);
            }

            // Ajouter les métadonnées de création
            properties.put("date_creation", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            properties.put("date_modif", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            log.info("Properties for new feature: {}", properties);

            // 5. Créer le feature à insérer
            Feature feature = Feature.builder()
                    .geometry(geometry)
                    .properties(properties)
                    .build();

            // 6. Exécuter l'ajout via WFS-T
            String newFeatureId = cartographicServerPort.insertFeature(layerCatalog, feature);

            if (newFeatureId != null) {
                log.info("Feature created successfully with ID {} in layer {}", newFeatureId, layerCatalog.name());
                return FeatureUpdateResult.builder()
                        .success(true)
                        .featureId(newFeatureId)
                        .message("Feature created successfully")
                        .build();
            } else {
                log.error("Failed to create feature in layer {}", layerCatalog.name());
                return FeatureUpdateResult.builder()
                        .success(false)
                        .message("WFS-T transaction failed")
                        .build();
            }

        } catch (Exception e) {
            log.error("Error creating feature in layer {}: {}", layerId, e.getMessage(), e);
            return FeatureUpdateResult.builder()
                    .success(false)
                    .message("Internal server error: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public FeatureUpdateResult updateFeature(UUID layerId, String featureId, FeatureUpdateRequest request) {
        try {
            log.info("Updating feature {} with request {}", featureId, request);

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

            // 2. Parser la géométrie
            FeatureGeometry geometry = parseGeometry(request.getGeometry());
            log.info("Parsed geometry: {}", geometry);
            if (geometry == null) {
                return FeatureUpdateResult.builder()
                        .success(false)
                        .featureId(featureId)
                        .message("Invalid geometry format")
                        .build();
            }

            // 3. Obtenir le schéma directement via GeoServer
            LayerSchema layerSchema = cartographicServerPort.getLayerSchema(
                    layerCatalog.workspace(), layerCatalog.geoserverLayerName());

            Map<String, String> attributeTypes = layerSchema.attributes().stream()
                    .collect(Collectors.toMap(LayerSchema.Attribute::label, LayerSchema.Attribute::javaType));

            // 4. Convertir les propriétés en fonction du type attendu
            Map<String, Object> updatedProperties = new HashMap<>();
            for (Map.Entry<String, Object> entry : request.getProperties().entrySet()) {
                String key = entry.getKey();
                Object rawValue = entry.getValue();
                String expectedType = attributeTypes.get(key);

                Object convertedValue = MappingUtils.convertValueToExpectedType(rawValue, expectedType);
                updatedProperties.put(key, convertedValue);
            }

            updatedProperties.put("date_modif", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            log.info("Updated properties: {}", updatedProperties);

            // 5. Créer le feature à mettre à jour
            Feature feature = Feature.builder()
                    .id(featureId)
                    .geometry(geometry)
                    .properties(updatedProperties)
                    .build();

            // 6. Exécuter la mise à jour via WFS-T
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

    public FeatureGeometry parseGeometry(String geometryJson) {
        try {
            JsonNode geometryNode = objectMapper.readTree(geometryJson);

            String type = geometryNode.get("type").asText();
            JsonNode coordsNode = geometryNode.get("coordinates");

            switch (type.toLowerCase()) {
                case "point":
                    return parsePoint(type, coordsNode);

                case "linestring":
                case "multilinestring":
                    return parseLineString(type, coordsNode);

                case "polygon":
                case "multipolygon":
                    return parsePolygon(type, coordsNode);

                default:
                    throw new IllegalArgumentException("Type de géométrie non supporté: " + type);
            }
        } catch (Exception e) {
            log.error("Erreur lors du parsing de la géométrie: {}", e.getMessage());
            throw new IllegalArgumentException("Erreur parsing géométrie", e);
        }
    }

    private FeatureGeometry parsePoint(String type, JsonNode coordsNode) {
        double[] pointCoords = parsePointCoordinates(coordsNode);
        return FeatureGeometry.builder()
                .type(type)
                .coordinates(pointCoords)
                .build();
    }

    private FeatureGeometry parseLineString(String type, JsonNode coordsNode) {
        double[] lineCoords;

        if ("multilinestring".equalsIgnoreCase(type)) {
            // MultiLineString: [ [[x1,y1],[x2,y2]], [[x3,y3],[x4,y4]] ]
            lineCoords = parseMultiLineStringCoordinates(coordsNode);
        } else {
            // LineString: [ [x1,y1],[x2,y2],[x3,y3] ]
            lineCoords = parseLineStringCoordinates(coordsNode);
        }

        return FeatureGeometry.builder()
                .type(type)
                .coordinates(lineCoords)
                .build();
    }

    private FeatureGeometry parsePolygon(String type, JsonNode coordsNode) {
        double[] polygonCoords;

        if ("multipolygon".equalsIgnoreCase(type)) {
            // MultiPolygon: [ [[[x1,y1],[x2,y2],[x3,y3],[x1,y1]]] ]
            polygonCoords = parseMultiPolygonCoordinates(coordsNode);
        } else {
            // Polygon: [ [[x1,y1],[x2,y2],[x3,y3],[x1,y1]] ]
            polygonCoords = parsePolygonCoordinates(coordsNode);
        }

        return FeatureGeometry.builder()
                .type(type)
                .coordinates(polygonCoords)
                .build();
    }

    // ===============================================
    // MÉTHODES DE PARSING SPÉCIALISÉES
    // ===============================================

    private double[] parsePointCoordinates(JsonNode coordsNode) {
        if (coordsNode.isArray() && coordsNode.size() >= 2) {
            return new double[] {
                    coordsNode.get(0).asDouble(),
                    coordsNode.get(1).asDouble()
            };
        }
        throw new IllegalArgumentException("Coordonnées Point invalides");
    }

    private double[] parseLineStringCoordinates(JsonNode coordsNode) {
        if (!coordsNode.isArray() || coordsNode.size() < 2) {
            throw new IllegalArgumentException("LineString doit avoir au moins 2 points");
        }

        List<Double> coords = new ArrayList<>();

        for (JsonNode pointNode : coordsNode) {
            if (pointNode.isArray() && pointNode.size() >= 2) {
                coords.add(pointNode.get(0).asDouble());
                coords.add(pointNode.get(1).asDouble());
            }
        }

        return coords.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private double[] parseMultiLineStringCoordinates(JsonNode coordsNode) {
        if (!coordsNode.isArray()) {
            throw new IllegalArgumentException("MultiLineString coordinates invalides");
        }

        List<Double> allCoords = new ArrayList<>();

        // Parcourir chaque LineString dans le MultiLineString
        for (JsonNode lineStringNode : coordsNode) {
            if (lineStringNode.isArray()) {
                for (JsonNode pointNode : lineStringNode) {
                    if (pointNode.isArray() && pointNode.size() >= 2) {
                        allCoords.add(pointNode.get(0).asDouble());
                        allCoords.add(pointNode.get(1).asDouble());
                    }
                }
                // Ajouter un marqueur de séparation entre les LineStrings
                // Utiliser des valeurs spéciales (ex: Double.NaN) ou une autre stratégie
                allCoords.add(Double.NaN);
                allCoords.add(Double.NaN);
            }
        }

        return allCoords.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private double[] parsePolygonCoordinates(JsonNode coordsNode) {
        if (!coordsNode.isArray() || coordsNode.isEmpty()) {
            throw new IllegalArgumentException("Polygon coordinates invalides");
        }

        List<Double> coords = new ArrayList<>();

        // Parcourir tous les rings (extérieur + intérieurs)
        for (JsonNode ringNode : coordsNode) {
            if (ringNode.isArray()) {
                for (JsonNode pointNode : ringNode) {
                    if (pointNode.isArray() && pointNode.size() >= 2) {
                        coords.add(pointNode.get(0).asDouble());
                        coords.add(pointNode.get(1).asDouble());
                    }
                }
                // Marqueur de fin de ring
                coords.add(Double.NaN);
                coords.add(Double.NaN);
            }
        }

        return coords.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private double[] parseMultiPolygonCoordinates(JsonNode coordsNode) {
        if (!coordsNode.isArray()) {
            throw new IllegalArgumentException("MultiPolygon coordinates invalides");
        }

        List<Double> allCoords = new ArrayList<>();

        // Parcourir chaque Polygon dans le MultiPolygon
        for (JsonNode polygonNode : coordsNode) {
            if (polygonNode.isArray()) {
                // Chaque polygon a ses rings
                for (JsonNode ringNode : polygonNode) {
                    if (ringNode.isArray()) {
                        for (JsonNode pointNode : ringNode) {
                            if (pointNode.isArray() && pointNode.size() >= 2) {
                                allCoords.add(pointNode.get(0).asDouble());
                                allCoords.add(pointNode.get(1).asDouble());
                            }
                        }
                        // Marqueur de fin de ring
                        allCoords.add(Double.NaN);
                        allCoords.add(Double.NaN);
                    }
                }
                // Marqueur de fin de polygon
                allCoords.add(Double.NEGATIVE_INFINITY);
                allCoords.add(Double.NEGATIVE_INFINITY);
            }
        }

        return allCoords.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
