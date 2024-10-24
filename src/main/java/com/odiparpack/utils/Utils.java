package com.odiparpack.utils;

import com.odiparpack.DataModel;
import com.odiparpack.models.Location;
import com.odiparpack.services.LocationService;

/**
 * La clase Utils contiene métodos utilitarios para cálculos y conversiones.
 */
public class Utils {

    /**
     * Calcula la distancia entre dos nodos utilizando sus coordenadas geográficas.
     *
     * @param fromUbigeo    Ubigeo de origen
     * @param toUbigeo Ubigeo destino
     * @return La distancia en kilómetros entre los dos nodos.
     */
    public static double calculateDistanceFromUbigeos(String fromUbigeo, String toUbigeo) {
        LocationService locationService = LocationService.getInstance();
        Location fromLocation = locationService.getLocation(fromUbigeo);
        Location toLocation = locationService.getLocation(toUbigeo);

        if (fromLocation == null || toLocation == null) {
            // Manejar error o devolver una distancia por defecto
            return 0.0;
        }

        double lat1 = fromLocation.getLatitude();
        double lon1 = fromLocation.getLongitude();
        double lat2 = toLocation.getLatitude();
        double lon2 = toLocation.getLongitude();

        return calculateDistance(lat1, lon1, lat2, lon2);
    }


    /**
     * Calcula la distancia entre dos puntos geográficos utilizando la fórmula de Haversine.
     *
     * @param lat1 Latitud del primer punto.
     * @param lon1 Longitud del primer punto.
     * @param lat2 Latitud del segundo punto.
     * @param lon2 Longitud del segundo punto.
     * @return La distancia en kilómetros entre los dos puntos.
     */
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

    /**
     * Calcula el tiempo de viaje entre dos nodos basado en la distancia y velocidad promedio.
     *
     * @param distanceKm       Distancia en kilómetros.
     * @param averageSpeedKmH  Velocidad promedio en km/h.
     * @return Tiempo de viaje en minutos.
     */
    public static long calculateTravelTime(double distanceKm, double averageSpeedKmH) {
        if (averageSpeedKmH <= 0) return Long.MAX_VALUE;
        double timeHours = distanceKm / averageSpeedKmH;
        return (long) (timeHours * 60); // Convertir a minutos
    }

    /**
     * Devuelve la velocidad promedio basada en las regiones de origen y destino.
     *
     * @param region1 Región de origen.
     * @param region2 Región de destino.
     * @return Velocidad promedio en km/h.
     */
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

    /**
     * Formatea el tiempo en minutos a una cadena legible.
     *
     * @param minutes Tiempo en minutos.
     * @return Cadena formateada.
     */
    public static String formatTime(long minutes) {
        long days = minutes / (24 * 60);
        long hours = (minutes % (24 * 60)) / 60;
        long mins = minutes % 60;
        return String.format("%d días, %d horas, %d minutos", days, hours, mins);
    }
}
