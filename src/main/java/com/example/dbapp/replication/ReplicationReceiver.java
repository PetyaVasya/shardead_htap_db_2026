package com.example.dbapp.replication;

public interface ReplicationReceiver {

    void apply(LogicalChange change);

    long appliedLsn();
}
