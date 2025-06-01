package com.amine.pfe.drawing_module.domain.model;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FeatureGeometry {
    private String type;
    private double[] coordinates;

    // Constructeur pour parsing direct depuis GeoJSON
    @JsonCreator
    public FeatureGeometry(@JsonProperty("type") String type, 
                          @JsonProperty("coordinates") Object coordinatesObj) {
        this.type = type;
        this.coordinates = parseCoordinatesDirectly(coordinatesObj);
    }
    
    private double[] parseCoordinatesDirectly(Object coordinatesObj) {
        if (coordinatesObj instanceof List) {
            List<?> coordsList = (List<?>) coordinatesObj;
            return coordsList.stream()
                .mapToDouble(coord -> ((Number) coord).doubleValue())
                .toArray();
        } else if (coordinatesObj instanceof Object[]) {
            Object[] coordsArray = (Object[]) coordinatesObj;
            return Arrays.stream(coordsArray)
                .mapToDouble(coord -> ((Number) coord).doubleValue())
                .toArray();
        }
        throw new IllegalArgumentException("Format de coordonnées non supporté");
    }
}
