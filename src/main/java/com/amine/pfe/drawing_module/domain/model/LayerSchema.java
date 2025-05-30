package com.amine.pfe.drawing_module.domain.model;

import java.util.List;

public record LayerSchema(
    String geometryType,
    List<Attribute> attributes
) {
    public record Attribute(String label, String type) {}
}
