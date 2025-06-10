package com.amine.pfe.drawing_module.domain.port.out;

import com.amine.pfe.drawing_module.domain.model.Feature;
import com.amine.pfe.drawing_module.domain.model.LayerCatalog;
import com.amine.pfe.drawing_module.domain.model.LayerSchema;

public interface CartographicServerPort {
    public LayerSchema getLayerSchema(String workspace, String layerName);
    public boolean updateFeature(LayerCatalog layerCatalog, Feature feature);
    public String insertFeature(LayerCatalog layerCatalog, Feature feature);
    public boolean deleteFeature(LayerCatalog layerCatalog, String featureId);
}
