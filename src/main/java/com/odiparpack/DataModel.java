package com.odiparpack;

import com.odiparpack.models.Vehicle;
import com.odiparpack.models.VehicleAssignment;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

    public DataModel(long[][] timeMatrix, List<VehicleAssignment> assignments,
                     Map<String, Integer> locationIndices, List<String> locationNames,
                     List<String> locationUbigeos) {
        //this.timeMatrix = timeMatrix;
        this.timeMatrix = Arrays.stream(timeMatrix)
                .map(long[]::clone)
                .toArray(long[][]::new);

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
    }

    public DataModel(long[][] timeMatrix, int[] starts, int[] ends,
                     List<String> locationNames, List<String> locationUbigeos) {
        this.timeMatrix = timeMatrix;
        this.assignments = null; // No hay asignaciones en este caso
        this.vehicleNumber = starts.length;
        this.locationNames = locationNames;
        this.locationUbigeos = locationUbigeos;

        this.starts = starts;
        this.ends = ends;
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