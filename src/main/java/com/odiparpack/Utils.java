package com.odiparpack;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;

public class Utils {
    private static final DateTimeFormatter BLOCKAGE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMdd,HH:mm");
    private static final DateTimeFormatter ORDER_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd HH:mm");

    public static long parseBlockageDateTimeToTimestamp(String dateTimeStr) {
        // Suponiendo que el año es 2024
        LocalDateTime dateTime = LocalDateTime.parse("2024" + dateTimeStr, DateTimeFormatter.ofPattern("yyyyMMdd,HH:mm"));
        return dateTime.toEpochSecond(ZoneOffset.UTC);
    }

    public static LocalDateTime parseBlockageDateTime(String dateTimeStr) {
        // Asumiendo que el formato es "mmdd,HH:mm"
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMdd,HH:mm");

        // Parsear la fecha y hora
        TemporalAccessor parsed = formatter.parse(dateTimeStr);

        // Extraer los componentes
        int month = parsed.get(ChronoField.MONTH_OF_YEAR);
        int dayOfMonth = parsed.get(ChronoField.DAY_OF_MONTH);
        int hour = parsed.get(ChronoField.HOUR_OF_DAY);
        int minute = parsed.get(ChronoField.MINUTE_OF_HOUR);

        // Obtener el año actual
        int currentYear = LocalDate.now().getYear();

        // Crear LocalDateTime
        return LocalDateTime.of(currentYear, month, dayOfMonth, hour, minute);
    }

    public static long parseOrderDateTimeToTimestamp(String dateTimeStr) {
        // Suponiendo que el mes y año son 2024-05 (por ejemplo)
        LocalDateTime dateTime = LocalDateTime.parse("2024-05-" + dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        return dateTime.toEpochSecond(ZoneOffset.UTC);
    }

    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Fórmula del Haversine
        int R = 6371; // Radio de la tierra en kilómetros
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c; // Distancia en kilómetros
        return distance;
    }

    public static double calculateDistanceFromNodes(DataModel data, int fromNode, int toNode) {
        // Usamos el tiempo de viaje como una aproximación de la distancia
        long travelTime = data.timeMatrix[fromNode][toNode];

        // Asumimos una velocidad promedio de 60 km/h para convertir tiempo en distancia
        // Esto es una aproximación y puede necesitar ajustes según el contexto de tu simulación
        double averageSpeed = 60.0; // km/h

        // Convertimos el tiempo (en minutos) a horas y luego a distancia en km
        double distanceKm = (travelTime / 60.0) * averageSpeed;
        // distancia = velocidad * tiempo
        return distanceKm;
    }

    public static double getAverageSpeed(String region1, String region2) {
        // Costa - Costa = 70 Km/h
        // Costa - Sierra = 50 Km/h
        // Sierra - Sierra = 60 Km/h
        // Sierra - Selva = 55 Km/h
        // Selva - Selva = 65 Km/h

        if (region1.equalsIgnoreCase("COSTA") && region2.equalsIgnoreCase("COSTA")) {
            return 70.0;
        } else if ((region1.equalsIgnoreCase("COSTA") && region2.equalsIgnoreCase("SIERRA")) ||
                (region1.equalsIgnoreCase("SIERRA") && region2.equalsIgnoreCase("COSTA"))) {
            return 50.0;
        } else if (region1.equalsIgnoreCase("SIERRA") && region2.equalsIgnoreCase("SIERRA")) {
            return 60.0;
        } else if ((region1.equalsIgnoreCase("SIERRA") && region2.equalsIgnoreCase("SELVA")) ||
                (region1.equalsIgnoreCase("SELVA") && region2.equalsIgnoreCase("SIERRA"))) {
            return 55.0;
        } else if (region1.equalsIgnoreCase("SELVA") && region2.equalsIgnoreCase("SELVA")) {
            return 65.0;
        } else {
            // Valor por defecto
            return 60.0;
        }
    }

    public static String formatTime(long minutes) {
        long days = minutes / (24 * 60);
        long hours = (minutes % (24 * 60)) / 60;
        long mins = minutes % 60;
        return String.format("%d días, %d horas, %d minutos", days, hours, mins);
    }
}
