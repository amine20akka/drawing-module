package com.amine.pfe.drawing_module.domain.model;

import java.util.UUID;

public record LayerCatalog(
    UUID layerId,
    String name,
    String geoserverLayerName,
    String workspace,
    String tableName
) {}