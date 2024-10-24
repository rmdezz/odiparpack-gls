package com.odiparpack.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.odiparpack.models.SimulationState;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@WebSocket
public class VehicleWebSocketHandler {

    private static Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private static Gson gson = new Gson();
    private static SimulationState simulationState;

    public VehicleWebSocketHandler() {
        // No-arg constructor required by Spark
    }

    // Provide a setter for simulationState
    public static void setSimulationState(SimulationState state) {
        simulationState = state;
    }

    public VehicleWebSocketHandler(SimulationState state) {
        simulationState = state;
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        sessions.add(session);
        System.out.println("Cliente conectado: " + session.getRemoteAddress().getAddress());
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        sessions.remove(session);
        System.out.println("Cliente desconectado: " + session.getRemoteAddress().getAddress());
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        // Manejar mensajes entrantes si es necesario
    }

    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        System.err.println("Error en WebSocket: " + error.getMessage());
    }

    public static void broadcastVehiclePositions() {
        if (simulationState != null) {
            JsonObject positions = simulationState.getCurrentPositionsGeoJSON();
            broadcast(gson.toJson(positions));
        }
    }

    private static void broadcast(String message) {
        for (Session session : sessions) {
            if (session.isOpen()) {
                try {
                    session.getRemote().sendString(message);
                } catch (IOException e) {
                    System.err.println("Error al enviar mensaje: " + e.getMessage());
                }
            }
        }
    }
}
