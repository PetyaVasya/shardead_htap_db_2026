package com.example.dbapp.cluster;

import java.util.List;
import java.util.Set;

public interface Topology {

    NodeRef leaderOf(ShardId shard);

    List<NodeRef> replicasOf(ShardId shard);

    Set<ShardId> localShards();
}
