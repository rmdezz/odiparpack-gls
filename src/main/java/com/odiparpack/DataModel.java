package com.odiparpack;

import com.odiparpack.models.Blockage;
import com.odiparpack.models.Vehicle;
import com.odiparpack.models.VehicleAssignment;
import com.odiparpack.models.Location;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.odiparpack.Main.locationIndices;
import static com.odiparpack.Main.logger;

public class DataModel {
    public final long[][] timeMatrix;
    public final int vehicleNumber;
    public final int[] starts;
    public final int[] ends;
    public final List<String> locationNames;
    public final List<String> locationUbigeos;
    public final List<VehicleAssignment> assignments;
    public final List<Blockage> activeBlockages;

    public DataModel(long[][] timeMatrix, List<Blockage> activeBlockages, List<VehicleAssignment> assignments,
                     Map<String, Integer> locationIndices, List<String> locationNames,
                     List<String> locationUbigeos) {
        //this.timeMatrix = timeMatrix;
        this.timeMatrix = Arrays.stream(timeMatrix)
                .map(long[]::clone)
                .toArray(long[][]::new);
        this.activeBlockages = activeBlockages
                .stream()
                .map(Blockage::clone)
                .collect(Collectors.toList());
        this.assignments = assignments;
        this.vehicleNumber = assignments.size();
        this.locationNames = locationNames;
        this.locationUbigeos = locationUbigeos;
        this.starts = new int[vehicleNumber];
        this.ends = new int[vehicleNumber];

        for (int i = 0; i < vehicleNumber; i++) {
            VehicleAssignment assignment = assignments.get(i);
            this.starts[i] = locationIndices.get(assignment.getVehicle().getCurrentLocationUbigeo());
            this.ends[i] = locationIndices.get(assignment.getOrder().getDestinationUbigeo());
        }

        printTravelTime("010501", "010301");

        logger.info("DataModel constructor with assignments:");
        for (int i = 0; i < vehicleNumber; i++) {
            VehicleAssignment assignment = assignments.get(i);
            String vehicleUbigeo = assignment.getVehicle().getCurrentLocationUbigeo();
            String orderDestinationUbigeo = assignment.getOrder().getDestinationUbigeo();
            Integer startIndex = locationIndices.get(vehicleUbigeo);
            Integer endIndex = locationIndices.get(orderDestinationUbigeo);

            if (startIndex == null || endIndex == null) {
                logger.warning("Ubigeo no encontrado en locationIndices: vehicleUbigeo=" + vehicleUbigeo + ", orderDestinationUbigeo=" + orderDestinationUbigeo);
            } else {
                this.starts[i] = startIndex;
                this.ends[i] = endIndex;
                logger.info("Vehículo " + assignment.getVehicle().getCode() + " inicia en ubigeo " + vehicleUbigeo + " (índice " + startIndex + "), termina en ubigeo " + orderDestinationUbigeo + " (índice " + endIndex + ")");
            }
        }

        logger.info("Índices de inicio (starts): " + Arrays.toString(this.starts));
        logger.info("Índices de fin (ends): " + Arrays.toString(this.ends));

        // Verificar los tiempos de viaje entre los índices de inicio y fin
        for (int i = 0; i < vehicleNumber; i++) {
            int startIdx = this.starts[i];
            int endIdx = this.ends[i];
            long travelTime = timeMatrix[startIdx][endIdx];
            logger.info("Tiempo de viaje desde índice " + startIdx + " a índice " + endIdx + " es " + travelTime);
        }
    }

    public DataModel(long[][] timeMatrix, List<Blockage> activeBlockages, int[] starts, int[] ends,
                     List<String> locationNames, List<String> locationUbigeos) {
        //this.timeMatrix = timeMatrix;
        this.timeMatrix = Arrays.stream(timeMatrix)
                .map(long[]::clone)
                .toArray(long[][]::new);
        this.activeBlockages = activeBlockages
                .stream()
                .map(Blockage::clone)
                .collect(Collectors.toList());
        this.assignments = null; // No hay asignaciones en este caso
        this.vehicleNumber = starts.length;
        this.locationNames = locationNames;
        this.locationUbigeos = locationUbigeos;
        this.starts = starts;
        this.ends = ends;

        logger.info("DataModel constructor without assignments:");
        logger.info("Índices de inicio (starts): " + Arrays.toString(this.starts));
        logger.info("Índices de fin (ends): " + Arrays.toString(this.ends));

        // Verificar los tiempos de viaje entre los índices de inicio y fin
        for (int i = 0; i < vehicleNumber; i++) {
            int startIdx = this.starts[i];
            int endIdx = this.ends[i];
            long travelTime = timeMatrix[startIdx][endIdx];
            logger.info("Tiempo de viaje desde índice " + startIdx + " a índice " + endIdx + " es " + travelTime);
        }
    }

    public void printTravelTime(String fromUbigeo, String toUbigeo) {
        // Obtener los índices de las ubicaciones en la matriz timeMatrix
        Integer fromIndex = locationIndices.get(fromUbigeo);
        Integer toIndex = locationIndices.get(toUbigeo);

        if (fromIndex != null && toIndex != null) {
            // Obtener el valor de la matriz para el trayecto específico
            long travelTime = timeMatrix[fromIndex][toIndex];

            // Registrar el valor usando el logger
            logger.info("El tiempo de viaje de " + fromUbigeo + " a " + toUbigeo + " es: " + travelTime);
        } else {
            logger.warning("Ubigeo no encontrado en los índices: de " + fromUbigeo + " a " + toUbigeo);
        }
    }

}