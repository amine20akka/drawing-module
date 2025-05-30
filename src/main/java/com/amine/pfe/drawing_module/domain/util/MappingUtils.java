package com.amine.pfe.drawing_module.domain.util;

public class MappingUtils {

    public static String mapGeometryTypeToDrawType(String gmlType) {
        return switch (gmlType) {
            case "PointPropertyType" -> "Point";
            case "LineStringPropertyType" -> "LineString";
            case "MultiLineStringPropertyType" -> "MultiLineString";
            case "PolygonPropertyType" -> "Polygon";
            case "MultiPolygonPropertyType" -> "MultiPolygon";
            default -> "Unknown";
        };
    }

    public static String mapXSDTypeToInputType(String xsdType) {
        return switch (xsdType) {
            // Types texte
            case "xsd:string" -> "text";
            case "xsd:anyURI" -> "url";

            // Types numériques
            case "xsd:int", "xsd:integer", "xsd:long", "xsd:short", "xsd:byte" -> "number";
            case "xsd:double", "xsd:float", "xsd:decimal" -> "number";
            case "xsd:unsignedInt", "xsd:unsignedLong", "xsd:unsignedShort", "xsd:unsignedByte" -> "number";
            case "xsd:positiveInteger", "xsd:negativeInteger", "xsd:nonPositiveInteger", "xsd:nonNegativeInteger" ->
                "number";

            // Types booléens
            case "xsd:boolean" -> "boolean";

            // Types temporels
            case "xsd:date" -> "date";
            case "xsd:dateTime" -> "datetime-local";
            case "xsd:time" -> "time";

            // Types personnalisés ou énumérations
            case "xsd:select" -> "select";

            // Types par défaut ou inconnus
            default -> {
                // Vérifier si c'est un type de texte long basé sur le nom
                if (xsdType.toLowerCase().contains("description") ||
                        xsdType.toLowerCase().contains("comment") ||
                        xsdType.toLowerCase().contains("note")) {
                    yield "textarea";
                }
                // Vérifier si c'est un email basé sur le nom
                else if (xsdType.toLowerCase().contains("email") ||
                        xsdType.toLowerCase().contains("mail")) {
                    yield "email";
                }
                // Par défaut : texte
                else {
                    yield "text";
                }
            }
        };
    }
}
