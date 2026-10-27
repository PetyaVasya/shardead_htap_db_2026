package com.example.dbapp.query;

public interface Planner {

    PhysicalPlan plan(String sql);
}
