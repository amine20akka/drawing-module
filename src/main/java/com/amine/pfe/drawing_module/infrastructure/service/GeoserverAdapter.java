package com.amine.pfe.drawing_module.infrastructure.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.amine.pfe.drawing_module.domain.dto.FeatureGeometry;
import com.amine.pfe.drawing_module.domain.model.Feature;
import com.amine.pfe.drawing_module.domain.model.LayerCatalog;
import com.amine.pfe.drawing_module.domain.model.LayerSchema;
import com.amine.pfe.drawing_module.domain.port.out.CartographicServerPort;
import com.amine.pfe.drawing_module.domain.util.MappingUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeoserverAdapter implements CartographicServerPort {

    private final RestTemplate restTemplate;

    @Value("${geoserver.url}")
    private String geoserverUrl;

    @Value("${geoserver.username}")
    private String username;

    @Value("${geoserver.password}")
    private String password;

    @Override
    public LayerSchema getLayerSchema(String workspace, String layerName) {
        String urlString = String.format(
                "%s/%s/ows?service=WFS&version=1.1.0&request=DescribeFeatureType&typeName=%s:%s",
                geoserverUrl, workspace, workspace, layerName);

        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8)));
            connection.setRequestMethod("GET");

            int status = connection.getResponseCode();
            if (status != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + status);
            }

            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }

                return parseDescribeFeatureType(response.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException("Error calling GeoServer DescribeFeatureType", e);
        }
    }

    private LayerSchema parseDescribeFeatureType(String xml) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new InputSource(new StringReader(xml)));

            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xpath = xPathFactory.newXPath();
            xpath.setNamespaceContext(new NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefix) {
                    return switch (prefix) {
                        case "xsd" -> "http://www.w3.org/2001/XMLSchema";
                        case "gml" -> "http://www.opengis.net/gml";
                        default -> XMLConstants.NULL_NS_URI;
                    };
                }

                @Override
                public String getPrefix(String namespaceURI) {
                    return null;
                }

                @Override
                public Iterator<String> getPrefixes(String namespaceURI) {
                    return null;
                }
            });

            // XPath pour cibler uniquement les éléments dans xsd:sequence
            XPathExpression expr = xpath.compile("//xsd:sequence/xsd:element");
            NodeList elements = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

            String geometryType = null;
            List<LayerSchema.Attribute> attributes = new ArrayList<>();

            for (int i = 0; i < elements.getLength(); i++) {
                Element el = (Element) elements.item(i);
                String name = el.getAttribute("name");
                String type = el.getAttribute("type");

                if (type.contains("gml:")) {
                    String extractedGeomType = type.replace("gml:", "");
                    geometryType = MappingUtils.mapGeometryTypeToDrawType(extractedGeomType);
                    continue;
                }

                if (isIgnoredField(name))
                    continue;

                attributes.add(new LayerSchema.Attribute(
                        labelize(name),
                        MappingUtils.mapXSDTypeToInputType(type)));
            }

            if (geometryType == null) {
                throw new IllegalStateException("No geometry type found in DescribeFeatureType");
            }

            return new LayerSchema(geometryType, attributes);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing DescribeFeatureType response", e);
        }
    }

    private boolean isIgnoredField(String fieldName) {
        return List.of("fid", "id", "gid").contains(fieldName.toLowerCase());
    }

    private String labelize(String fieldName) {
        return Arrays.stream(fieldName.split("_"))
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    @Override
    public boolean updateFeature(LayerCatalog layerCatalog, Feature feature) {
        try {
            log.info("Executing WFS-T Update for feature {} in layer {} (GeoServer: {})",
                    feature.getId(), layerCatalog.name(), layerCatalog.geoserverLayerName());

            // Construire la requête WFS-T XML
            String wfsTransaction = buildWfsUpdateTransaction(layerCatalog, feature);

            // Configurer les headers
            HttpHeaders headers = new HttpHeaders();
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encodedAuth);
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.set("Accept", "application/xml");

            HttpEntity<String> request = new HttpEntity<>(wfsTransaction, headers);

            // Exécuter la requête
            ResponseEntity<String> response = restTemplate.exchange(
                    geoserverUrl + "/" + "/wfs",
                    HttpMethod.POST,
                    request,
                    String.class);

            // Analyser la réponse
            boolean success = parseWfsResponse(response.getBody());

            if (success) {
                log.info("WFS-T Update successful for feature {} in layer {}",
                        feature.getId(), layerCatalog.name());
            } else {
                log.error("WFS-T Update failed for feature {} in layer {}",
                        feature.getId(), layerCatalog.name());
                log.debug("WFS Response: {}", response.getBody());
            }

            return success;

        } catch (Exception e) {
            log.error("Error executing WFS-T Update for feature {} in layer {}: {}",
                    feature.getId(), layerCatalog.name(), e.getMessage(), e);
            return false;
        }
    }

    private String buildWfsUpdateTransaction(LayerCatalog layerCatalog, Feature feature) {
        // Convertir la géométrie au format GML correct
        String geometryGml = convertGeometryToGml(feature.getGeometry());

        // Construire les propriétés à mettre à jour
        String propertyUpdates = feature.getProperties().entrySet().stream()
                .filter(entry -> !isEmptyValue(entry.getValue()))
                .map(entry -> String.format(
                        "<wfs:Property><wfs:Name>%s</wfs:Name><wfs:Value>%s</wfs:Value></wfs:Property>",
                        entry.getKey(),
                        escapeXml(String.valueOf(entry.getValue()))))
                .collect(Collectors.joining("\n"));

        return String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <wfs:Transaction version="1.1.0" service="WFS"
                    xmlns:wfs="http://www.opengis.net/wfs"
                    xmlns:ogc="http://www.opengis.net/ogc"
                    xmlns:gml="http://www.opengis.net/gml">
                    <wfs:Update typeName="%s:%s">
                        <wfs:Property>
                            <wfs:Name>geom</wfs:Name>
                            <wfs:Value>%s</wfs:Value>
                        </wfs:Property>
                        %s
                        <ogc:Filter>
                            <ogc:FeatureId fid="%s"/>
                        </ogc:Filter>
                    </wfs:Update>
                </wfs:Transaction>
                """,
                layerCatalog.workspace(),
                layerCatalog.geoserverLayerName(),
                geometryGml,
                propertyUpdates,
                feature.getId());
    }

    /**
     * Vérifie si une valeur est considérée comme vide
     * 
     * @param value la valeur à vérifier
     * @return true si la valeur est vide, false sinon
     */
    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }

        String stringValue = String.valueOf(value).trim();

        // Considérer comme vide si :
        // - chaîne vide
        // - chaîne "null" (au cas où)
        // - chaîne "undefined" (au cas où)
        return stringValue.isEmpty() ||
                "null".equalsIgnoreCase(stringValue) ||
                "undefined".equalsIgnoreCase(stringValue);
    }

    private String convertGeometryToGml(FeatureGeometry geometry) {
        switch (geometry.getType().toLowerCase()) {
            case "point":
                return buildPointGml(geometry.getCoordinates());
            case "linestring":
                return buildLineStringGml(geometry.getCoordinates());
            case "polygon":
                return buildPolygonGml(geometry.getCoordinates());
            default:
                throw new IllegalArgumentException("Type de géométrie non supporté: " + geometry.getType());
        }
    }

    private String buildPointGml(Object coordinates) {
        double[] coords = extractCoordinates(coordinates);
        if (coords.length < 2) {
            throw new IllegalArgumentException("Point doit avoir au moins 2 coordonnées");
        }

        // Utiliser le format GML 3.1.1 avec srsDimension explicite et locale US pour
        // les points décimaux
        return String.format(Locale.US,
                "<gml:Point srsName=\"EPSG:3857\" srsDimension=\"2\">" +
                        "<gml:pos>%.6f %.6f</gml:pos>" +
                        "</gml:Point>",
                coords[0], coords[1]);
    }

    private String buildLineStringGml(Object coordinates) {
        if (!(coordinates instanceof Object[])) {
            throw new IllegalArgumentException("LineString coordinates doit être un tableau");
        }

        Object[] coordsArray = (Object[]) coordinates;
        StringBuilder coordsBuilder = new StringBuilder();

        for (int i = 0; i < coordsArray.length; i++) {
            double[] point = extractCoordinates(coordsArray[i]);
            if (point.length < 2)
                continue;

            if (i > 0)
                coordsBuilder.append(" ");
            coordsBuilder.append(String.format(Locale.US, "%.6f %.6f", point[0], point[1]));
        }

        return String.format(
                "<gml:LineString srsName=\"EPSG:3857\" srsDimension=\"2\">" +
                        "<gml:posList>%s</gml:posList>" +
                        "</gml:LineString>",
                coordsBuilder.toString());
    }

    private String buildPolygonGml(Object coordinates) {
        if (!(coordinates instanceof Object[])) {
            throw new IllegalArgumentException("Polygon coordinates doit être un tableau");
        }

        Object[] rings = (Object[]) coordinates;
        if (rings.length == 0) {
            throw new IllegalArgumentException("Polygon doit avoir au moins un ring");
        }

        StringBuilder polygonBuilder = new StringBuilder();
        polygonBuilder.append("<gml:Polygon srsName=\"EPSG:3857\" srsDimension=\"2\">");

        // Premier ring = exterior
        Object[] exteriorRing = (Object[]) rings[0];
        StringBuilder exteriorCoords = new StringBuilder();

        for (int i = 0; i < exteriorRing.length; i++) {
            double[] point = extractCoordinates(exteriorRing[i]);
            if (point.length < 2)
                continue;

            if (i > 0)
                exteriorCoords.append(" ");
            exteriorCoords.append(String.format(Locale.US, "%.6f %.6f", point[0], point[1]));
        }

        polygonBuilder.append("<gml:exterior><gml:LinearRing>");
        polygonBuilder.append(String.format("<gml:posList>%s</gml:posList>", exteriorCoords.toString()));
        polygonBuilder.append("</gml:LinearRing></gml:exterior>");

        // Rings intérieurs (trous)
        for (int r = 1; r < rings.length; r++) {
            Object[] interiorRing = (Object[]) rings[r];
            StringBuilder interiorCoords = new StringBuilder();

            for (int i = 0; i < interiorRing.length; i++) {
                double[] point = extractCoordinates(interiorRing[i]);
                if (point.length < 2)
                    continue;

                if (i > 0)
                    interiorCoords.append(" ");
                interiorCoords.append(String.format(Locale.US, "%.6f %.6f", point[0], point[1]));
            }

            polygonBuilder.append("<gml:interior><gml:LinearRing>");
            polygonBuilder.append(String.format("<gml:posList>%s</gml:posList>", interiorCoords.toString()));
            polygonBuilder.append("</gml:LinearRing></gml:interior>");
        }

        polygonBuilder.append("</gml:Polygon>");
        return polygonBuilder.toString();
    }

    private double[] extractCoordinates(Object coordinates) {
        double[] coords = extractCoordinatesOriginal(coordinates);

        // Forcer en 2D (X, Y seulement)
        if (coords.length >= 2) {
            return new double[] { coords[0], coords[1] };
        }

        return coords;
    }

    private double[] extractCoordinatesOriginal(Object coordinates) {
        // Cas 1: Object[] (tableau d'objets)
        if (coordinates instanceof Object[]) {
            Object[] coordsArray = (Object[]) coordinates;
            double[] result = new double[coordsArray.length];

            for (int i = 0; i < coordsArray.length; i++) {
                if (coordsArray[i] instanceof Number) {
                    result[i] = ((Number) coordsArray[i]).doubleValue();
                } else if (coordsArray[i] instanceof String) {
                    try {
                        result[i] = Double.parseDouble((String) coordsArray[i]);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Impossible de convertir la coordonnée: " + coordsArray[i]);
                    }
                } else {
                    throw new IllegalArgumentException("Type de coordonnée non supporté: " + coordsArray[i].getClass());
                }
            }
            return result;
        }
        // Cas 2: List<Number> (liste de nombres)
        else if (coordinates instanceof java.util.List) {
            java.util.List<?> coordsList = (java.util.List<?>) coordinates;
            double[] result = new double[coordsList.size()];

            for (int i = 0; i < coordsList.size(); i++) {
                Object coord = coordsList.get(i);
                if (coord instanceof Number) {
                    result[i] = ((Number) coord).doubleValue();
                } else if (coord instanceof String) {
                    try {
                        result[i] = Double.parseDouble((String) coord);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Impossible de convertir la coordonnée: " + coord);
                    }
                } else {
                    throw new IllegalArgumentException(
                            "Type de coordonnée non supporté dans la liste: " + coord.getClass());
                }
            }
            return result;
        }
        // Cas 3: String JSON comme "[-59740.77, 5339847.06]"
        else if (coordinates instanceof String) {
            String coordStr = (String) coordinates;
            if (coordStr.startsWith("[") && coordStr.endsWith("]")) {
                coordStr = coordStr.substring(1, coordStr.length() - 1);
                String[] parts = coordStr.split(",");
                double[] result = new double[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    result[i] = Double.parseDouble(parts[i].trim());
                }
                return result;
            }
        }
        // Cas 4: double[] directement
        else if (coordinates instanceof double[]) {
            return (double[]) coordinates;
        }
        // Cas 5: float[]
        else if (coordinates instanceof float[]) {
            float[] floatArray = (float[]) coordinates;
            double[] result = new double[floatArray.length];
            for (int i = 0; i < floatArray.length; i++) {
                result[i] = floatArray[i];
            }
            return result;
        }

        // Debug: afficher le type exact et la valeur
        System.err.println("Type de coordonnées: " + coordinates.getClass().getName());
        System.err.println("Valeur: " + coordinates.toString());

        throw new IllegalArgumentException("Format de coordonnées non reconnu: " + coordinates + " (type: "
                + coordinates.getClass().getName() + ")");
    }

    private String escapeXml(String value) {
        if (value == null)
            return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private boolean parseWfsResponse(String xmlResponse) {
        if (xmlResponse == null)
            return false;

        // Vérifier si la transaction a réussi
        return xmlResponse.contains("<wfs:totalUpdated>1</wfs:totalUpdated>") ||
                xmlResponse.contains("totalUpdated>1</") ||
                (!xmlResponse.contains("<ows:Exception") &&
                        !xmlResponse.contains("<ServiceException"));
    }
}
