package com.odiparpack.simulation.order;

import com.odiparpack.models.Order;
import com.odiparpack.models.Vehicle;
import com.odiparpack.models.VehicleAssignment;
import com.odiparpack.models.WarehouseManager;
import com.odiparpack.simulation.vehicle.VehicleManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class OrderManager {
    private static final Logger logger = Logger.getLogger(OrderManager.class.getName());

    private List<Order> orders;

    public OrderManager(List<Order> orders) {
        this.orders = orders;
    }

    public void updateOrderStatuses(LocalDateTime currentTime, WarehouseManager warehouseManager) {
        for (Order order : orders) {
            if (order.getStatus() == Order.OrderStatus.PENDING_PICKUP) {
                if (order.isReadyForDelivery(currentTime)) {
                    order.setDelivered(currentTime);
                    warehouseManager.increaseCapacity(order.getDestinationUbigeo(), order.getQuantity());
                    logger.info("Orden " + order.getId() + " entregada completamente.");
                }
            }
        }
    }

    public void planOrders(LocalDateTime currentTime, VehicleManager vehicleManager, long[][] timeMatrix) {
        List<Order> availableOrders = getAvailableOrders(currentTime);
        List<Vehicle> availableVehicles = vehicleManager.getAvailableVehicles();

        if (!availableOrders.isEmpty() && !availableVehicles.isEmpty()) {
            List<VehicleAssignment> assignments = assignOrdersToVehicles(availableOrders, availableVehicles, currentTime, vehicleManager, timeMatrix);
            if (!assignments.isEmpty()) {
                vehicleManager.assignRoutesToVehicles(assignments, currentTime, timeMatrix);
            }
        } else {
            logger.info("No hay órdenes o vehículos disponibles para planificación.");
        }
    }

    private List<Order> getAvailableOrders(LocalDateTime currentTime) {
        return orders.stream()
                .filter(order -> (order.getStatus() == Order.OrderStatus.REGISTERED
                        || order.getStatus() == Order.OrderStatus.PARTIALLY_ASSIGNED
                        || order.getStatus() == Order.OrderStatus.PARTIALLY_ARRIVED)
                        && !order.getOrderTime().isAfter(currentTime))
                .collect(Collectors.toList());
    }

    private static List<VehicleAssignment> assignOrdersToVehicles(List<Order> orders, List<Vehicle> vehicles,
                                                                  LocalDateTime currentTime,
                                                                  VehicleManager vehicleManager,
                                                                  long[][] timeMatrix) {
        List<VehicleAssignment> assignments = new ArrayList<>();

        // Ordenar los pedidos por dueTime (los más urgentes primero)
        orders.sort(Comparator.comparing(Order::getDueTime));

        for (Order order : orders) {
            if (order.getStatus() != Order.OrderStatus.REGISTERED &&
                    order.getStatus() != Order.OrderStatus.PARTIALLY_ASSIGNED &&
                    order.getStatus() != Order.OrderStatus.PARTIALLY_ARRIVED) {
                logger.info("Orden " + order.getId() + " no está en estado REGISTERED, PARTIALLY_ASSIGNED o PARTIALLY_ARRIVED. Se omite.");
                continue;
            }

            int unassignedPackages = order.getUnassignedPackages();
            if (unassignedPackages <= 0) {
                continue; // No hay paquetes por asignar
            }

            List<Vehicle> availableVehicles = vehicleManager.getAvailableVehicles(vehicles, order.getOriginUbigeo()).stream()
                    .sorted(Comparator.comparingInt(Vehicle::getCapacity).reversed()) // Ordenar por capacidad descendente
                    .collect(Collectors.toList());

            if (availableVehicles.isEmpty()) {
                logger.info("No hay vehículos disponibles en " + order.getOriginUbigeo() + " para la orden " + order.getId());
                continue;
            }

            for (Vehicle vehicle : availableVehicles) {
                if (unassignedPackages <= 0) {
                    break; // No hay más paquetes que asignar
                }

                if (vehicle.getCapacity() >= unassignedPackages) {
                    // El vehículo puede satisfacer completamente la orden
                    assignments.add(new VehicleAssignment(vehicle, order, unassignedPackages));
                    vehicle.setAvailable(false);
                    vehicle.setEstado(Vehicle.EstadoVehiculo.ORDENES_CARGADAS);
                    order.incrementAssignedPackages(unassignedPackages); // Actualización completa

                    String logMessage = String.format(
                            "\n--- Asignación Completa ---\n" +
                                    "Código de la Orden: %d\n" +
                                    "Cantidad Total de la Orden: %d paquetes\n" +
                                    "Cantidad Asignada al Vehículo: %d paquetes\n" +
                                    "Código del Vehículo: %s\n" +
                                    "---------------------------",
                            order.getId(),
                            order.getQuantity(),
                            unassignedPackages,
                            vehicle.getCode()
                    );
                    logger.info(logMessage);

                    //order.setAssignedPackages(unassignedPackages);
                    unassignedPackages = 0;
                    break;
                } else if (vehicle.getCapacity() > 0) {
                    // El vehículo puede satisfacer parcialmente la orden
                    int assignedQuantity = Math.min(vehicle.getCapacity(), unassignedPackages); // Limitar a paquetes restantes
                    assignments.add(new VehicleAssignment(vehicle, order, assignedQuantity));
                    vehicle.setAvailable(false);
                    vehicle.setEstado(Vehicle.EstadoVehiculo.ORDENES_CARGADAS);
                    order.incrementAssignedPackages(assignedQuantity); // Actualización parcial

                    String logMessage = String.format(
                            "\n--- Asignación Parcial ---\n" +
                                    "Código de la Orden: %d\n" +
                                    "Cantidad Total de la Orden: %d paquetes\n" +
                                    "Cantidad Asignada al Vehículo: %d paquetes\n" +
                                    "Código del Vehículo: %s\n" +
                                    "---------------------------",
                            order.getId(),
                            order.getQuantity(),
                            assignedQuantity,
                            vehicle.getCode()
                    );
                    logger.info(logMessage);

                    unassignedPackages -= assignedQuantity;
                }
            }

            if (unassignedPackages > 0) {
                order.setStatus(Order.OrderStatus.PARTIALLY_ASSIGNED);
                logger.warning("Quedan " + unassignedPackages + " paquetes por asignar para la orden " + order.getId());
            } else {
                order.setStatus(Order.OrderStatus.FULLY_ASSIGNED);
                logger.info("Orden " + order.getId() + " completamente asignada.");
            }
        }

        return assignments;
    }


}
