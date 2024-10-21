package com.odiparpack;

import com.google.gson.Gson;
import com.odiparpack.models.SimulationState;

import static spark.Spark.*;

import java.util.List;

public class BreakdownServer {
    private final SimulationState simulationState;
    private final Gson gson = new Gson();

    public BreakdownServer(SimulationState simulationState) {
        this.simulationState = simulationState;
    }

    public void start() {
        port(4567);

        // Endpoint de bienvenida
        get("/", (request, response) -> {
            response.type("text/html");
            return "<h1>Bienvenido al Servidor de Averías</h1>" +
                    "<p>Para provocar una avería, envía una solicitud POST a /breakdown con los parámetros vehicleCode y breakdownType.</p>" +
                    "<p>Para ver los mensajes de averías de un vehículo, envía una solicitud GET a /breakdown/messages?vehicleCode=YOUR_VEHICLE_CODE</p>";
        });

        // Endpoint para provocar una avería
        post("/breakdown", (request, response) -> {
            String vehicleCode = request.queryParams("vehicleCode");
            String breakdownType = request.queryParams("breakdownType");

            if (vehicleCode == null || breakdownType == null) {
                response.status(400);
                return "Parámetros faltantes: vehicleCode y breakdownType son obligatorios.";
            }

            // Validar el tipo de avería
            if (!isValidBreakdownType(breakdownType)) {
                response.status(400);
                return "Tipo de avería inválido. Debe ser 1, 2 o 3.";
            }

            // Provocar la avería en el estado de simulación
            simulationState.provocarAveria(vehicleCode, breakdownType);

            response.status(200);
            return "Avería procesada para el vehículo " + vehicleCode;
        });

        // Nuevo Endpoint para obtener mensajes de averías
        get("/breakdown/messages", (request, response) -> {
            String vehicleCode = request.queryParams("vehicleCode");

            if (vehicleCode == null) {
                response.status(400);
                return "Parámetro faltante: vehicleCode es obligatorio.";
            }

            List<String> messages = simulationState.getBreakdownLogs().get(vehicleCode);

            if (messages == null || messages.isEmpty()) {
                response.status(404);
                return "No se encontraron mensajes de avería para el vehículo " + vehicleCode;
            }

            response.type("application/json");
            return gson.toJson(messages);
        });

        System.out.println("Servidor de averías iniciado en http://localhost:4567");
    }

    // Método para validar el tipo de avería
    private boolean isValidBreakdownType(String breakdownType) {
        return breakdownType.equals("1") || breakdownType.equals("2") || breakdownType.equals("3");
    }
}