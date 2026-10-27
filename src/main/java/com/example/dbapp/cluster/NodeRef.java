package com.example.dbapp.cluster;

public record NodeRef(String id) {
    public NodeRef {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("node id must not be blank");
        }
    }
}
