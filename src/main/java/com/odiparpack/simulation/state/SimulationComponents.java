package com.odiparpack.simulation.state;


import com.odiparpack.models.WarehouseManager;
import com.odiparpack.simulation.blockage.BlockageManager;
import com.odiparpack.simulation.maintenance.MaintenanceManager;
import com.odiparpack.simulation.order.OrderManager;
import com.odiparpack.simulation.route.RouteManager;
import com.odiparpack.simulation.vehicle.VehicleManager;

import java.time.LocalDateTime;

/**
 * La clase SimulationComponents encapsula todos los componentes necesarios para el estado de la simulación.
 */
public class SimulationComponents {
    private final LocalDateTime initialTime;
    private final VehicleManager vehicleManager;
    private final OrderManager orderManager;
    private final RouteManager routeManager;
    private final BlockageManager blockageManager;
    private final MaintenanceManager maintenanceManager;
    private final WarehouseManager warehouseManager;

    public SimulationComponents(LocalDateTime initialTime, VehicleManager vehicleManager, OrderManager orderManager,
                                RouteManager routeManager, BlockageManager blockageManager,
                                MaintenanceManager maintenanceManager, WarehouseManager warehouseManager) {
        this.initialTime = initialTime;
        this.vehicleManager = vehicleManager;
        this.orderManager = orderManager;
        this.routeManager = routeManager;
        this.blockageManager = blockageManager;
        this.maintenanceManager = maintenanceManager;
        this.warehouseManager = warehouseManager;
    }

    public LocalDateTime getInitialTime() {
        return initialTime;
    }

    public VehicleManager getVehicleManager() {
        return vehicleManager;
    }

    public OrderManager getOrderManager() {
        return orderManager;
    }

    public RouteManager getRouteManager() {
        return routeManager;
    }

    public BlockageManager getBlockageManager() {
        return blockageManager;
    }

    public MaintenanceManager getMaintenanceManager() {
        return maintenanceManager;
    }

    public WarehouseManager getWarehouseManager() {
        return warehouseManager;
    }
}
