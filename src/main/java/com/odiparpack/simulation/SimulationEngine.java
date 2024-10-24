package com.odiparpack.simulation;

import com.odiparpack.simulation.scheduler.SimulationScheduler;
import com.odiparpack.simulation.state.SimulationState;

public class SimulationEngine {
    private SimulationState simulationState;
    private SimulationScheduler simulationScheduler;

    public SimulationEngine(SimulationState state) {
        this.simulationState = state;
        this.simulationScheduler = new SimulationScheduler(state);
    }

    // Método para verificar si la simulación está en ejecución
    public boolean isRunning() {
        return simulationScheduler.isSimulationRunning();
    }

    public void startSimulation() {
        simulationScheduler.start();
    }

    public void pauseSimulation() {
        simulationScheduler.pause();
    }

    public void resumeSimulation() {
        simulationScheduler.resume();
    }

    public void stopSimulation() {
        simulationScheduler.stop();
    }
}
