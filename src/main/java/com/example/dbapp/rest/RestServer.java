package com.example.dbapp.rest;

public interface RestServer extends AutoCloseable {

    void start();

    @Override
    void close();
}
