package com.amine.pfe.drawing_module.domain.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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

    public static String mapXSDTypeToJavaType(String xsdType) {
        return switch (xsdType) {
            // Chaînes de caractères
            case "xsd:string", "xsd:anyURI" -> "String";

            // Entiers
            case "xsd:int", "xsd:integer" -> "Integer";
            case "xsd:long" -> "Long";
            case "xsd:short" -> "Short";
            case "xsd:byte" -> "Byte";
            case "xsd:unsignedInt", "xsd:unsignedShort", "xsd:unsignedByte" -> "Integer";
            case "xsd:unsignedLong" -> "Long";
            case "xsd:positiveInteger", "xsd:nonNegativeInteger" -> "Integer";
            case "xsd:negativeInteger", "xsd:nonPositiveInteger" -> "Integer";

            // Nombres décimaux
            case "xsd:float" -> "Float";
            case "xsd:double" -> "Double";
            case "xsd:decimal" -> "BigDecimal";

            // Booléens
            case "xsd:boolean" -> "Boolean";

            // Dates
            case "xsd:date" -> "LocalDate";
            case "xsd:dateTime" -> "LocalDateTime";
            case "xsd:time" -> "LocalTime";

            // Par défaut
            default -> "String";
        };
    }

    public static Object convertValueToExpectedType(Object value, String javaType) {
        if (value == null)
            return null;

        try {
            return switch (javaType) {
                case "String" -> value.toString();
                case "Integer" -> Integer.valueOf(value.toString());
                case "Long" -> Long.valueOf(value.toString());
                case "Short" -> Short.valueOf(value.toString());
                case "Float" -> Float.valueOf(value.toString());
                case "Double" -> Double.valueOf(value.toString());
                case "BigDecimal" -> new BigDecimal(value.toString());
                case "Boolean" -> Boolean.valueOf(value.toString());
                case "LocalDate" -> LocalDate.parse(value.toString());
                case "LocalDateTime" -> LocalDateTime.parse(value.toString());
                case "LocalTime" -> LocalTime.parse(value.toString());
                default -> value.toString();
            };
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot convert value " + value + " to " + javaType, e);
        }
    }
}
