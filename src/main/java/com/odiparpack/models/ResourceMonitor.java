package com.odiparpack.models;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.List;

public class ResourceMonitor {
    private static final OperatingSystemMXBean osBean =
            ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

    private volatile boolean running = false;
    private final List<Long> timestamps = new ArrayList<>();
    private final List<Double> cpuUsages = new ArrayList<>();
    private final List<Long> memoryUsages = new ArrayList<>();
    private Thread monitorThread;

    public void startMonitoring() {
        running = true;
        monitorThread = new Thread(() -> {
            while (running) {
                long timestamp = System.currentTimeMillis();
                double cpuUsage = osBean.getProcessCpuLoad() * 100; // Porcentaje de CPU
                long memoryUsage = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024); // Memoria usada en MB

                synchronized (this) {
                    timestamps.add(timestamp);
                    cpuUsages.add(cpuUsage);
                    memoryUsages.add(memoryUsage);
                }

                try {
                    Thread.sleep(500); // Muestreo cada 500 ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        monitorThread.start();
    }

    public void stopMonitoring() {
        running = false;
        try {
            monitorThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public synchronized List<Long> getTimestamps() {
        return new ArrayList<>(timestamps);
    }

    public synchronized List<Double> getCpuUsages() {
        return new ArrayList<>(cpuUsages);
    }

    public synchronized List<Long> getMemoryUsages() {
        return new ArrayList<>(memoryUsages);
    }
}
