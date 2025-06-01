package com.amine.pfe.drawing_module.infrastructure.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

import com.amine.pfe.drawing_module.domain.model.Feature;
import com.amine.pfe.drawing_module.domain.model.FeatureGeometry;
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

                String inputType = MappingUtils.mapXSDTypeToInputType(type);
                String javaType = MappingUtils.mapXSDTypeToJavaType(type);
                attributes.add(new LayerSchema.Attribute(name, inputType, javaType));
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
            headers.setContentType(new MediaType("application", "xml", StandardCharsets.UTF_8));
            headers.set("Accept", "application/xml");
            headers.set("Accept-Charset", "UTF-8");


            HttpEntity<String> request = new HttpEntity<>(
                    new String(wfsTransaction.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8),
                    headers);

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

    public String convertGeometryToGml(FeatureGeometry geometry) {
        switch (geometry.getType().toLowerCase()) {
            case "point":
                return convertPointToGml(geometry.getCoordinates());

            case "linestring":
                return convertLineStringToGml(geometry.getCoordinates());

            case "multilinestring":
                return convertMultiLineStringToGml(geometry.getCoordinates());

            case "polygon":
                return convertPolygonToGml(geometry.getCoordinates());

            case "multipolygon":
                return convertMultiPolygonToGml(geometry.getCoordinates());

            default:
                throw new IllegalArgumentException("Type géométrie non supporté: " + geometry.getType());
        }
    }

    private String convertPointToGml(double[] coordinates) {
        return String.format(Locale.US,
                "<gml:Point srsName=\"EPSG:3857\" srsDimension=\"2\">" +
                        "<gml:pos>%.6f %.6f</gml:pos>" +
                        "</gml:Point>",
                coordinates[0], coordinates[1]);
    }

    private String convertLineStringToGml(double[] coordinates) {
        StringBuilder coordsBuilder = new StringBuilder();

        for (int i = 0; i < coordinates.length; i += 2) {
            if (i > 0)
                coordsBuilder.append(" ");
            coordsBuilder.append(String.format(Locale.US, "%.6f %.6f",
                    coordinates[i], coordinates[i + 1]));
        }

        return String.format(
                "<gml:LineString srsName=\"EPSG:3857\" srsDimension=\"2\">" +
                        "<gml:posList>%s</gml:posList>" +
                        "</gml:LineString>",
                coordsBuilder.toString());
    }

    private String convertMultiLineStringToGml(double[] coordinates) {
        StringBuilder multiLineBuilder = new StringBuilder();
        multiLineBuilder.append("<gml:MultiLineString srsName=\"EPSG:3857\">");

        StringBuilder currentLine = new StringBuilder();

        for (int i = 0; i < coordinates.length; i += 2) {
            if (Double.isNaN(coordinates[i])) {
                // Fin d'une LineString
                if (currentLine.length() > 0) {
                    multiLineBuilder.append("<gml:lineStringMember>");
                    multiLineBuilder.append("<gml:LineString srsDimension=\"2\">");
                    multiLineBuilder.append("<gml:posList>").append(currentLine.toString()).append("</gml:posList>");
                    multiLineBuilder.append("</gml:LineString>");
                    multiLineBuilder.append("</gml:lineStringMember>");
                    currentLine = new StringBuilder();
                }
            } else {
                if (currentLine.length() > 0)
                    currentLine.append(" ");
                currentLine.append(String.format(Locale.US, "%.6f %.6f",
                        coordinates[i], coordinates[i + 1]));
            }
        }

        multiLineBuilder.append("</gml:MultiLineString>");
        return multiLineBuilder.toString();
    }

    private String convertPolygonToGml(double[] coordinates) {
        StringBuilder polygonBuilder = new StringBuilder();
        polygonBuilder.append("<gml:Polygon srsName=\"EPSG:3857\" srsDimension=\"2\">");

        StringBuilder currentRing = new StringBuilder();
        boolean isFirstRing = true;

        for (int i = 0; i < coordinates.length; i += 2) {
            if (Double.isNaN(coordinates[i])) {
                // Fin d'un ring
                if (currentRing.length() > 0) {
                    if (isFirstRing) {
                        polygonBuilder.append("<gml:exterior><gml:LinearRing>");
                        polygonBuilder.append("<gml:posList>").append(currentRing.toString()).append("</gml:posList>");
                        polygonBuilder.append("</gml:LinearRing></gml:exterior>");
                        isFirstRing = false;
                    } else {
                        polygonBuilder.append("<gml:interior><gml:LinearRing>");
                        polygonBuilder.append("<gml:posList>").append(currentRing.toString()).append("</gml:posList>");
                        polygonBuilder.append("</gml:LinearRing></gml:interior>");
                    }
                    currentRing = new StringBuilder();
                }
            } else {
                if (currentRing.length() > 0)
                    currentRing.append(" ");
                currentRing.append(String.format(Locale.US, "%.6f %.6f",
                        coordinates[i], coordinates[i + 1]));
            }
        }

        polygonBuilder.append("</gml:Polygon>");
        return polygonBuilder.toString();
    }

    private String convertMultiPolygonToGml(double[] coordinates) {
        StringBuilder multiPolygonBuilder = new StringBuilder();
        multiPolygonBuilder.append("<gml:MultiPolygon srsName=\"EPSG:3857\">");

        StringBuilder currentPolygon = new StringBuilder();
        StringBuilder currentRing = new StringBuilder();
        boolean isFirstRing = true;
        boolean polygonStarted = false;

        for (int i = 0; i < coordinates.length; i += 2) {
            if (Double.isInfinite(coordinates[i])) {
                // Fin d'un polygon - fermer le ring et le polygon actuels si nécessaire
                if (currentRing.length() > 0) {
                    if (isFirstRing) {
                        currentPolygon.append("<gml:exterior><gml:LinearRing>");
                        currentPolygon.append("<gml:posList>").append(currentRing.toString()).append("</gml:posList>");
                        currentPolygon.append("</gml:LinearRing></gml:exterior>");
                    } else {
                        currentPolygon.append("<gml:interior><gml:LinearRing>");
                        currentPolygon.append("<gml:posList>").append(currentRing.toString()).append("</gml:posList>");
                        currentPolygon.append("</gml:LinearRing></gml:interior>");
                    }
                    currentRing = new StringBuilder();
                }

                if (currentPolygon.length() > 0) {
                    multiPolygonBuilder.append("<gml:polygonMember>");
                    multiPolygonBuilder.append(currentPolygon.toString());
                    multiPolygonBuilder.append("</gml:Polygon>");
                    multiPolygonBuilder.append("</gml:polygonMember>");
                }

                // Préparer pour un nouveau polygon
                currentPolygon = new StringBuilder();
                currentPolygon.append("<gml:Polygon srsDimension=\"2\">");
                isFirstRing = true;
                polygonStarted = true;

            } else if (Double.isNaN(coordinates[i])) {
                // Fin d'un ring
                if (currentRing.length() > 0) {
                    if (!polygonStarted) {
                        currentPolygon.append("<gml:Polygon srsDimension=\"2\">");
                        polygonStarted = true;
                    }

                    if (isFirstRing) {
                        currentPolygon.append("<gml:exterior><gml:LinearRing>");
                        currentPolygon.append("<gml:posList>").append(currentRing.toString()).append("</gml:posList>");
                        currentPolygon.append("</gml:LinearRing></gml:exterior>");
                        isFirstRing = false;
                    } else {
                        currentPolygon.append("<gml:interior><gml:LinearRing>");
                        currentPolygon.append("<gml:posList>").append(currentRing.toString()).append("</gml:posList>");
                        currentPolygon.append("</gml:LinearRing></gml:interior>");
                    }
                    currentRing = new StringBuilder();
                }
            } else {
                // Coordonnées normales
                if (currentRing.length() > 0)
                    currentRing.append(" ");
                currentRing.append(String.format(Locale.US, "%.6f %.6f",
                        coordinates[i], coordinates[i + 1]));
            }
        }

        // Fermer le dernier ring et polygon s'ils existent
        if (currentRing.length() > 0) {
            if (!polygonStarted) {
                currentPolygon.append("<gml:Polygon srsDimension=\"2\">");
                polygonStarted = true;
            }

            if (isFirstRing) {
                currentPolygon.append("<gml:exterior><gml:LinearRing>");
                currentPolygon.append("<gml:posList>").append(currentRing.toString()).append("</gml:posList>");
                currentPolygon.append("</gml:LinearRing></gml:exterior>");
            } else {
                currentPolygon.append("<gml:interior><gml:LinearRing>");
                currentPolygon.append("<gml:posList>").append(currentRing.toString()).append("</gml:posList>");
                currentPolygon.append("</gml:LinearRing></gml:interior>");
            }
        }

        if (currentPolygon.length() > 0 && polygonStarted) {
            multiPolygonBuilder.append("<gml:polygonMember>");
            multiPolygonBuilder.append(currentPolygon.toString());
            multiPolygonBuilder.append("</gml:Polygon>");
            multiPolygonBuilder.append("</gml:polygonMember>");
        }

        multiPolygonBuilder.append("</gml:MultiPolygon>");
        return multiPolygonBuilder.toString();
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
