package com.amine.pfe.drawing_module.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import com.amine.pfe.drawing_module.domain.model.LayerCatalog;

public interface LayerRepositoryPort {
    Optional<LayerCatalog> findLayerCatalogById(UUID layerId);
}
