package com.odiparpack.simulation.scheduler;

import com.odiparpack.simulation.state.SimulationState;
import com.odiparpack.websocket.VehicleWebSocketHandler;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimulationScheduler {
    private static final Logger logger = Logger.getLogger(SimulationScheduler.class.getName());
    private SimulationState simulationState;
    private ScheduledExecutorService executorService;
    private ScheduledExecutorService webSocketExecutorService;
    private AtomicBoolean isSimulationRunning;
    private static final int TIME_ADVANCEMENT_INTERVAL_MINUTES = 5;
    private static final int SIMULATION_SPEED = 10; // 1 minuto de simulación = 1 segundo de tiempo real
    private static final int PLANNING_INTERVAL_MINUTES = 15;
    private static final int SIMULATION_DAYS = 7;

    public SimulationScheduler(SimulationState state) {
        this.simulationState = state;
        this.executorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() + 2);
        this.isSimulationRunning = new AtomicBoolean(false);
    }

    public boolean isSimulationRunning() {
        return isSimulationRunning.get();
    }

    public void start() {
        if (isSimulationRunning.get()) {
            logger.info("La simulación ya está en ejecución.");
            return;
        }
        isSimulationRunning.set(true);

        scheduleTimeAdvancement();
        schedulePlanning();
        scheduleWebSocketBroadcast();
    }

    public void pause() {
        simulationState.pauseSimulation();
    }

    public void resume() {
        simulationState.resumeSimulation();
    }

    public void stop() {
        isSimulationRunning.set(false);
        simulationState.stopSimulation();
        executorService.shutdownNow();
        if (webSocketExecutorService != null && !webSocketExecutorService.isShutdown()) {
            webSocketExecutorService.shutdownNow();
            webSocketExecutorService = null;
        }
    }

    private void scheduleTimeAdvancement() {
        LocalDateTime endTime = simulationState.getCurrentTime().plusDays(SIMULATION_DAYS);
        executorService.scheduleAtFixedRate(() -> {
            try {
                if (!isSimulationRunning.get() || simulationState.isPaused() || simulationState.isStopped()) return;

                simulationState.advanceTime(TIME_ADVANCEMENT_INTERVAL_MINUTES);
                logger.info("Tiempo de simulación: " + simulationState.getCurrentTime());

                simulationState.updateSimulationState();

                if (simulationState.getCurrentTime().isAfter(endTime)) {
                    logger.info("Simulación completada.");
                    isSimulationRunning.set(false);
                    simulationState.stopSimulation();
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error en la tarea de avance del tiempo", e);
            }
        }, 0, TIME_ADVANCEMENT_INTERVAL_MINUTES * 1000L / SIMULATION_SPEED, TimeUnit.MILLISECONDS);
    }

    private void schedulePlanning() {
        executorService.scheduleAtFixedRate(() -> {
            if (!isSimulationRunning.get() || simulationState.isPaused() || simulationState.isStopped()) return;

            logger.info("Iniciando algoritmo de planificación en tiempo de simulación: " + simulationState.getCurrentTime());

            try {
                simulationState.getOrderManager().planOrders(simulationState.getCurrentTime(), simulationState.getVehicleManager(), simulationState.getCurrentTimeMatrix());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error en el ciclo de planificación", e);
            }
        }, 0, PLANNING_INTERVAL_MINUTES * 1000L / SIMULATION_SPEED, TimeUnit.MILLISECONDS);
    }

    private void scheduleWebSocketBroadcast() {
        webSocketExecutorService = Executors.newSingleThreadScheduledExecutor();
        webSocketExecutorService.scheduleAtFixedRate(() -> {
            try {
                if (simulationState.isPaused() || simulationState.isStopped()) return;

                // Broadcast vehicle positions via WebSocket
                VehicleWebSocketHandler.broadcastVehiclePositions();

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in WebSocket broadcast task", e);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }
}
