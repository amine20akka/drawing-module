package com.amine.pfe.drawing_module.infrastructure.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.amine.pfe.drawing_module.domain.model.LayerCatalog;
import com.amine.pfe.drawing_module.domain.port.out.LayerRepositoryPort;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JdbcLayerRepositoryAdapter implements LayerRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<LayerCatalog> findLayerCatalogById(UUID layerId) {
        String sql = "SELECT * FROM drawing.layer_catalog WHERE layer_id = ?";
        try {
            return jdbcTemplate.query(
                    sql,
                    ps -> ps.setObject(1, layerId),
                    (rs, rowNum) -> new LayerCatalog(
                            UUID.fromString(rs.getString("layer_id")),
                            rs.getString("name"),
                            rs.getString("geoserver_layer_name"),
                            rs.getString("workspace"),
                            rs.getString("table_name")))
                    .stream().findFirst();
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
