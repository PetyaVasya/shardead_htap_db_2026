package com.example.dbapp.cluster;

import com.example.dbapp.common.ShardKey;

public interface ShardLocator {

    ShardId locate(ShardKey key);
}
