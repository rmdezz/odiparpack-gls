/*
package com.odiparpack;

import com.odiparpack.models.RouteCache;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import com.google.ortools.Loader;

@SpringBootApplication
public class OdiparPackApplication {

    public static void main(String[] args) {
        Loader.loadNativeLibraries();
        ConfigurableApplicationContext context = SpringApplication.run(OdiparPackApplication.class, args);

        SimulationRunner runner = context.getBean(SimulationRunner.class);
        runner.runSimulation();
    }

    @Bean
    public DataLoader dataLoader() {
        return new DataLoader();
    }

    @Bean
    public RouteCache routeCache() {
        return new RouteCache(Main.ROUTE_CACHE_CAPACITY);
    }
}*/
