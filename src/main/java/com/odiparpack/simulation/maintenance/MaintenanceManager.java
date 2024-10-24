package com.odiparpack.simulation.maintenance;

import com.odiparpack.models.Maintenance;
import com.odiparpack.models.Vehicle;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * La clase MaintenanceManager gestiona las operaciones de mantenimiento de los vehículos,
 * incluyendo la verificación de estados de mantenimiento y la transición de vehículos hacia
 * y desde el mantenimiento.
 */
public class MaintenanceManager {
    private static final Logger logger = Logger.getLogger(MaintenanceManager.class.getName());

    private final List<Maintenance> maintenanceSchedule;

    /**
     * Constructor de MaintenanceManager.
     *
     * @param maintenanceSchedule Lista de programaciones de mantenimiento.
     */
    public MaintenanceManager(List<Maintenance> maintenanceSchedule) {
        this.maintenanceSchedule = maintenanceSchedule;
    }

    /**
     * Verifica si un vehículo está actualmente en mantenimiento.
     *
     * @param vehicle      El vehículo a verificar.
     * @param currentTime  El tiempo actual de la simulación.
     * @return true si el vehículo está en mantenimiento, false de lo contrario.
     */
    public boolean isVehicleUnderMaintenance(Vehicle vehicle, LocalDateTime currentTime) {
        Maintenance currentMaintenance = getCurrentMaintenance(vehicle.getCode(), currentTime);
        return currentMaintenance != null;
    }

    /**
     * Maneja la transición de un vehículo hacia el mantenimiento.
     *
     * @param vehicle     El vehículo a poner en mantenimiento.
     * @param currentTime El tiempo actual de la simulación.
     */
    public void handleVehicleInMaintenance(Vehicle vehicle, LocalDateTime currentTime) {
        Maintenance maintenance = getCurrentMaintenance(vehicle.getCode(), currentTime);
        if (maintenance != null) {
            Vehicle.EstadoVehiculo estado = vehicle.getEstado();

            // Verificar si el vehículo está en tránsito hacia una orden o almacén
            if (estado == Vehicle.EstadoVehiculo.EN_TRANSITO_ORDEN || estado == Vehicle.EstadoVehiculo.HACIA_ALMACEN) {
                logger.info("Vehículo " + vehicle.getCode() + " está en tránsito. Programando mantenimiento después de la entrega.");
            }
            // Verificar si el vehículo está listo para retornar o en espera en la oficina
            else if (estado == Vehicle.EstadoVehiculo.LISTO_PARA_RETORNO || estado == Vehicle.EstadoVehiculo.EN_ESPERA_EN_OFICINA) {
                logger.info("Vehículo " + vehicle.getCode() + " está en espera. Programando mantenimiento después del retorno.");
            }
            // Si el vehículo está en almacén y no tiene órdenes cargadas
            else if (estado == Vehicle.EstadoVehiculo.EN_ALMACEN) {
                vehicle.setEstado(Vehicle.EstadoVehiculo.EN_MANTENIMIENTO);
                vehicle.setAvailable(false);
                logger.info("Vehículo " + vehicle.getCode() + " enviado a mantenimiento hasta " + maintenance.getEndTime());
            }
            // Si el vehículo ya está en mantenimiento, registrar la información
            else if (estado == Vehicle.EstadoVehiculo.EN_MANTENIMIENTO) {
                logger.info("Vehículo " + vehicle.getCode() + " ya está en mantenimiento.");
            }
            // Verificar si el vehículo está averiado
            else if (estado == Vehicle.EstadoVehiculo.AVERIADO_1 || estado == Vehicle.EstadoVehiculo.AVERIADO_2 || estado == Vehicle.EstadoVehiculo.AVERIADO_3) {
                logger.info("Vehículo " + vehicle.getCode() + " está averiado. No puede ir a mantenimiento hasta ser reparado.");
            }
        }
    }

    /**
     * Obtiene la programación de mantenimiento actual para un vehículo.
     *
     * @param vehicleCode  El código del vehículo.
     * @param currentTime  El tiempo actual de la simulación.
     * @return La instancia de Maintenance si existe, null de lo contrario.
     */
    public Maintenance getCurrentMaintenance(String vehicleCode, LocalDateTime currentTime) {
        return maintenanceSchedule.stream()
                .filter(m -> m.getVehicleCode().equals(vehicleCode) && m.isInMaintenancePeriod(currentTime))
                .findFirst()
                .orElse(null);
    }

    /**
     * Actualiza el tiempo de avería de un vehículo.
     *
     * @param vehicle      El vehículo a actualizar.
     * @param currentTime  El tiempo actual de la simulación.
     */
    public void updateBreakdownTime(Vehicle vehicle, LocalDateTime currentTime) {
        vehicle.updateBreakdownTime(currentTime);
        logger.info("Tiempo de avería actualizado para el vehículo " + vehicle.getCode() + " a " + currentTime);
    }

    /**
     * Obtiene todas las programaciones de mantenimiento para un vehículo.
     *
     * @param vehicleCode El código del vehículo.
     * @return Lista de programaciones de mantenimiento.
     */
    public List<Maintenance> getMaintenanceScheduleForVehicle(String vehicleCode) {
        return maintenanceSchedule.stream()
                .filter(m -> m.getVehicleCode().equals(vehicleCode))
                .collect(Collectors.toList());
    }
}
