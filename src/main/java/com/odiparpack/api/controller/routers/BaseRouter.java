package com.odiparpack.api.controller.routers;

import com.google.gson.Gson;

public abstract class BaseRouter {
    protected final Gson gson = new Gson();

    // Método que cada router debe implementar
    public abstract void setupRoutes();

    // Método helper para transformar objetos a JSON
    protected String toJson(Object object) {
        return gson.toJson(object);
    }
}