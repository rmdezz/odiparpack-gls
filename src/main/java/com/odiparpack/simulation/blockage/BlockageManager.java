package com.odiparpack.simulation.blockage;

import com.odiparpack.models.Blockage;

import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class BlockageManager {
    private static final Logger logger = Logger.getLogger(BlockageManager.class.getName());

    private List<Blockage> allBlockages;
    private List<Blockage> activeBlockages;
    private long[][] originalTimeMatrix;
    private long[][] currentTimeMatrix;
    private Map<String, Integer> locationIndices;

    public BlockageManager(List<Blockage> allBlockages, long[][] timeMatrix, Map<String, Integer> locationIndices) {
        this.allBlockages = allBlockages;
        this.activeBlockages = new ArrayList<>();
        this.originalTimeMatrix = timeMatrix;
        // Crear una copia de la matriz de tiempo original para modificaciones
        this.currentTimeMatrix = Arrays.stream(timeMatrix)
                .map(long[]::clone)
                .toArray(long[][]::new);
        this.locationIndices = locationIndices;
    }

    public void updateBlockages(LocalDateTime currentTime) {
        // Remover bloqueos que han expirado
        List<Blockage> expiredBlockages = activeBlockages.stream()
                .filter(blockage -> currentTime.isAfter(blockage.getEndTime()))
                .collect(Collectors.toList());

        activeBlockages.removeAll(expiredBlockages);

        // Añadir nuevos bloqueos activos
        for (Blockage blockage : allBlockages) {
            if (!currentTime.isBefore(blockage.getStartTime()) &&
                    currentTime.isBefore(blockage.getEndTime()) &&
                    !activeBlockages.contains(blockage)) {
                activeBlockages.add(blockage);
            }
        }

        // Actualizar la matriz de tiempo
        updateTimeMatrix();
    }

    private void updateTimeMatrix() {
        // Restaurar la matriz original
        for (int i = 0; i < currentTimeMatrix.length; i++) {
            System.arraycopy(originalTimeMatrix[i], 0, currentTimeMatrix[i], 0, originalTimeMatrix[i].length);
        }

        // Aplicar bloqueos activos
        for (Blockage blockage : activeBlockages) {
            Integer fromIndex = locationIndices.get(blockage.getOriginUbigeo());
            Integer toIndex = locationIndices.get(blockage.getDestinationUbigeo());
            if (fromIndex != null && toIndex != null) {
                currentTimeMatrix[fromIndex][toIndex] = Long.MAX_VALUE;
                currentTimeMatrix[toIndex][fromIndex] = Long.MAX_VALUE; // Asumiendo rutas bidireccionales
            } else {
                logger.warning("Índices de ubicación no encontrados para bloqueo: " + blockage);
            }
        }
    }

    public List<Blockage> getActiveBlockages() {
        return new ArrayList<>(activeBlockages);
    }

    public long[][] getCurrentTimeMatrix() {
        // Devolver una copia para evitar modificaciones externas
        return Arrays.stream(currentTimeMatrix)
                .map(long[]::clone)
                .toArray(long[][]::new);
    }
}
