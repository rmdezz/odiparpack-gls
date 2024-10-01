package com.odiparpack.models;

import java.util.*;

public class RouteCache {
    private final int capacity;
    private final Map<String, List<RouteSegment>> cache;
    private final Queue<String> lruQueue;

    public RouteCache(int capacity) {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.lruQueue = new LinkedList<>();
    }

    public List<RouteSegment> getRoute(String from, String to, List<Blockage> activeBlockages) {
        String directKey = from + "-" + to;
        String reverseKey = to + "-" + from;

        List<RouteSegment> route = null;
        String key = null;

        if (cache.containsKey(directKey)) {
            route = cache.get(directKey);
            key = directKey;
        } else if (cache.containsKey(reverseKey)) {
            route = reverseRoute(cache.get(reverseKey));
            key = reverseKey;
        }

        if (route != null && isRouteValid(route, activeBlockages)) {
            updateLRU(key);
            return route;
        }

        return null;
    }

    private boolean isRouteValid(List<RouteSegment> route, List<Blockage> activeBlockages) {
        for (RouteSegment segment : route) {
            for (Blockage blockage : activeBlockages) {
                String[] locations = segment.getName().split(" to ");
                String fromUbigeo = locations[0];
                String toUbigeo = segment.getUbigeo(); // El ubigeo representa el destino del segmento

                if (fromUbigeo.equals(blockage.getOriginUbigeo()) &&
                        toUbigeo.equals(blockage.getDestinationUbigeo())) {
                    return false;
                }
            }
        }
        return true;
    }

    private void updateLRU(String key) {
        lruQueue.remove(key);
        lruQueue.offer(key);
    }

    private List<RouteSegment> reverseRoute(List<RouteSegment> route) {
        List<RouteSegment> reversedRoute = new ArrayList<>();

        for (int i = route.size() - 1; i >= 0; i--) {
            RouteSegment originalSegment = route.get(i);

            // Extraer los nombres de las ubicaciones del nombre del segmento
            String[] locations = originalSegment.getName().split(" to ");
            String fromName = locations[1];  // El "to" de la ruta original se convierte en el "from" de la ruta invertida
            String toName = locations[0];    // El "from" de la ruta original se convierte en el "to" de la ruta invertida

            // Crear un nuevo segmento con los nombres invertidos
            RouteSegment reversedSegment = new RouteSegment(
                    fromName + " to " + toName,
                    originalSegment.getUbigeo(),  // El ubigeo se mantiene igual (representa el destino del segmento)
                    originalSegment.getDistance(),
                    originalSegment.getDurationMinutes()
            );

            reversedRoute.add(reversedSegment);
        }

        return reversedRoute;
    }

    public void putRoute(String from, String to, List<RouteSegment> route) {
        String key = from + "-" + to;
        if (cache.size() >= capacity && !cache.containsKey(key)) {
            String lruKey = lruQueue.poll();
            cache.remove(lruKey);
        }
        cache.put(key, route);
        lruQueue.offer(key);
    }
}