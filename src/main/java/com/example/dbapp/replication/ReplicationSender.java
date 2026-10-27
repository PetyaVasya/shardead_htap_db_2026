package com.example.dbapp.replication;

import com.example.dbapp.cluster.NodeRef;

public interface ReplicationSender {

    void replicate(LogicalChange change);

    void catchUp(NodeRef target, long fromLsn);
}
