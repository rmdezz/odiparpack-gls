package com.odiparpack.models;

import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.odiparpack.DataModel;

public class RoutingResult {
    public Assignment solution;
    public RoutingModel routingModel;
    public RoutingIndexManager manager;
    public DataModel data;

    public RoutingResult(Assignment solution, RoutingModel routingModel, RoutingIndexManager manager, DataModel data) {
        this.solution = solution;
        this.routingModel = routingModel;
        this.manager = manager;
        this.data = data;
    }
}
