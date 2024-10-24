package com.odiparpack.simulation.state;

import com.google.gson.JsonObject;
import com.odiparpack.models.WarehouseManager;
import com.odiparpack.services.DataService;
import com.odiparpack.services.LocationService;
import com.odiparpack.simulation.blockage.BlockageManager;
import com.odiparpack.simulation.maintenance.MaintenanceManager;
import com.odiparpack.simulation.order.OrderManager;
import com.odiparpack.simulation.route.RouteManager;
import com.odiparpack.simulation.vehicle.VehicleManager;

import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * La clase SimulationState es responsable de gestionar el estado global de la simulación,
 * coordinando las interacciones entre los diferentes managers y servicios.
 */
public class SimulationState {
    private static final Logger logger = Logger.getLogger(SimulationState.class.getName());

    // Managers para manejar diferentes aspectos de la simulación
    private final VehicleManager vehicleManager;
    private final OrderManager orderManager;
    private final RouteManager routeManager;
    private final BlockageManager blockageManager;
    private final MaintenanceManager maintenanceManager;
    private final WarehouseManager warehouseManager;

    // Estado y control de tiempo de la simulación
    private LocalDateTime currentTime;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean isPaused = false;
    private volatile boolean isStopped = false;

    /**
     * Constructor que inicializa el estado de la simulación y carga los datos iniciales.
     */
    public SimulationState() {
        LocationService.getInstance(); // Inicializar el singleton de LocationService
        DataService dataService = new DataService(); // Instancia de DataService

        // Cargar datos iniciales y configurar managers
        SimulationInitializer initializer = new SimulationInitializer(dataService);
        SimulationComponents components = initializer.initializeComponents();

        this.currentTime = components.getInitialTime();
        this.vehicleManager = components.getVehicleManager();
        this.orderManager = components.getOrderManager();
        this.routeManager = components.getRouteManager();
        this.blockageManager = components.getBlockageManager();
        this.maintenanceManager = components.getMaintenanceManager();
        this.warehouseManager = components.getWarehouseManager();

        logger.info("SimulationState initialized successfully.");
    }

    public long[][] getCurrentTimeMatrix() {
        return blockageManager.getCurrentTimeMatrix();
    }

    /**
     * Actualiza el estado de la simulación, incluyendo bloqueos, vehículos y órdenes.
     */
    public void updateSimulationState() {
        blockageManager.updateBlockages(currentTime);
        vehicleManager.updateVehicleStates(currentTime, getCurrentTimeMatrix());
        orderManager.updateOrderStatuses(currentTime, warehouseManager);
    }

    /**
     * Pausa la simulación.
     */
    public void pauseSimulation() {
        isPaused = true;
    }

    /**
     * Reanuda la simulación.
     */
    public void resumeSimulation() {
        isPaused = false;
    }

    /**
     * Detiene la simulación.
     */
    public void stopSimulation() {
        isStopped = true;
    }

    /**
     * Verifica si la simulación está pausada.
     */
    public boolean isPaused() {
        return isPaused;
    }

    /**
     * Verifica si la simulación está detenida.
     */
    public boolean isStopped() {
        return isStopped;
    }

    /**
     * Obtiene el tiempo actual de la simulación de manera segura.
     */
    public LocalDateTime getCurrentTime() {
        lock.lock();
        try {
            return currentTime;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Avanza el tiempo de la simulación en minutos.
     */
    public void advanceTime(int minutes) {
        lock.lock();
        try {
            this.currentTime = this.currentTime.plusMinutes(minutes);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Obtiene las posiciones actuales de los vehículos en formato GeoJSON.
     */
    public JsonObject getCurrentPositionsGeoJSON() {
        return vehicleManager.getCurrentPositionsGeoJSON(currentTime);
    }

    public OrderManager getOrderManager() {
        return orderManager;
    }

    public VehicleManager getVehicleManager() {
        return vehicleManager;
    }
}
