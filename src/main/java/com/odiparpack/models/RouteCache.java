package com.odiparpack.models;

import java.util.*;
import java.util.stream.Collectors;

import static com.odiparpack.Main.logger;

public class RouteCache {
    private final int capacity;
    private final Map<String, List<VersionedRoute>> cache;
    private final Queue<String> lruQueue;

    public RouteCache(int capacity) {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.lruQueue = new LinkedList<>();
    }

    public List<RouteSegment> getRoute(String from, String to, List<Blockage> activeBlockages) {
        String directKey = from + "-" + to;
        String reverseKey = to + "-" + from;

        logger.info("Buscando ruta para " + from + " a " + to);
        logger.info("Bloqueos activos: " + activeBlockages);

        List<RouteSegment> route = null;
        String key = null;

        if (cache.containsKey(directKey)) {
            logger.info("Encontradas versiones de ruta directa en caché para " + directKey);
            printCachedVersions(directKey);
            route = getBestCompatibleRoute(cache.get(directKey), activeBlockages);
            key = directKey;
        } else if (cache.containsKey(reverseKey)) {
            logger.info("Encontradas versiones de ruta inversa en caché para " + reverseKey);
            printCachedVersions(reverseKey);
            List<RouteSegment> reverseRoute = getBestCompatibleRoute(cache.get(reverseKey), activeBlockages);
            if (reverseRoute != null) {
                route = reverseRoute(reverseRoute);
                key = reverseKey;
            }
        } else {
            logger.info("No se encontraron rutas en caché para " + directKey + " ni " + reverseKey);
        }

        if (route != null) {
            logger.info("Ruta compatible encontrada. Actualizando LRU para " + key);
            updateLRU(key);
            logger.info("Ruta seleccionada: " + routeToString(route));
            return route;
        }

        logger.info("No se encontró ninguna ruta compatible en caché.");
        return null;
    }

    private void printCachedVersions(String key) {
        List<VersionedRoute> versions = cache.get(key);
        logger.info("Versiones de ruta en caché para " + key + ":");
        for (int i = 0; i < versions.size(); i++) {
            VersionedRoute vr = versions.get(i);
            logger.info("Versión " + (i + 1) + ":");
            logger.info("  Duración total: " + vr.getTotalDuration() + " minutos");
            logger.info("  Bloqueos activos cuando se creó: " + blockagesToString(vr.getActiveBlockages()));
            logger.info("  Ruta: " + routeToString(vr.getRoute()));
        }
    }

    private String blockagesToString(Collection<Blockage> blockages) {
        if (blockages == null || blockages.isEmpty()) {
            return "[]";
        }
        return blockages.stream()
                .map(Blockage::toString)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String routeToString(List<RouteSegment> route) {
        StringBuilder sb = new StringBuilder();
        for (RouteSegment segment : route) {
            if (sb.length() > 0) sb.append(" -> ");
            sb.append(segment.getName());
        }
        return sb.toString();
    }

    private List<RouteSegment> getBestCompatibleRoute(List<VersionedRoute> versionedRoutes, List<Blockage> activeBlockages) {
        return versionedRoutes.stream()
                .filter(vr -> isRouteValid(vr.getRoute(), activeBlockages))
                .min(Comparator.comparingLong(VersionedRoute::getTotalDuration))
                .map(VersionedRoute::getRoute)
                .orElse(null);
    }

    private boolean isRouteValid(List<RouteSegment> route, List<Blockage> activeBlockages) {
        for (RouteSegment segment : route) {
            for (Blockage blockage : activeBlockages) {
                String[] locations = segment.getName().split(" to ");
                String fromUbigeo = locations[0];
                String toUbigeo = segment.getUbigeo();

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
            String[] locations = originalSegment.getName().split(" to ");
            String fromName = locations[1];
            String toName = locations[0];

            RouteSegment reversedSegment = new RouteSegment(
                    fromName + " to " + toName,
                    originalSegment.getToUbigeo(),
                    originalSegment.getFromUbigeo(),
                    originalSegment.getDistance(),
                    originalSegment.getDurationMinutes()
            );

            reversedRoute.add(reversedSegment);
        }

        return reversedRoute;
    }

    public void putRoute(String from, String to, List<RouteSegment> route, List<Blockage> activeBlockages) {
        String key = from + "-" + to;
        if (!cache.containsKey(key)) {
            if (cache.size() >= capacity) {
                String lruKey = lruQueue.poll();
                cache.remove(lruKey);
            }
            cache.put(key, new ArrayList<>());
            lruQueue.offer(key);
        }

        long totalDuration = route.stream().mapToLong(RouteSegment::getDurationMinutes).sum();
        VersionedRoute versionedRoute = new VersionedRoute(route, new HashSet<>(activeBlockages), totalDuration);
        List<VersionedRoute> routes = cache.get(key);
        routes.add(versionedRoute);

        // Opcional: limitar el número de versiones por ruta
        if (routes.size() > 5) {  // Por ejemplo, mantener solo las 5 versiones más recientes
            routes.remove(0);
        }
    }

    public void clear() {
        logger.info("Limpiando RouteCache...");

        // Registrar estado antes de limpiar
        logger.info("Estado antes de limpiar:");
        logger.info("  Número de rutas en caché: " + cache.size());
        logger.info("  Número de entradas en LRU queue: " + lruQueue.size());

        // Limpiar el mapa de caché
        cache.clear();

        // Limpiar la cola LRU
        lruQueue.clear();

        logger.info("RouteCache limpiado completamente");
    }

    private static class VersionedRoute {
        private List<RouteSegment> route;
        private Set<Blockage> activeBlockages;
        private long totalDuration;

        public VersionedRoute(List<RouteSegment> route, Set<Blockage> activeBlockages, long totalDuration) {
            this.route = route;
            this.activeBlockages = activeBlockages;
            this.totalDuration = totalDuration;
        }

        public List<RouteSegment> getRoute() {
            return route;
        }

        public Set<Blockage> getActiveBlockages() {
            return activeBlockages;
        }

        public long getTotalDuration() {
            return totalDuration;
        }
    }
}