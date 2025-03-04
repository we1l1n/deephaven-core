/**
 * Copyright (c) 2016-2022 Deephaven Data Labs and Patent Pending
 */
package io.deephaven.client.impl;

import io.grpc.ManagedChannel;
import org.apache.arrow.memory.BufferAllocator;

import java.util.concurrent.ScheduledExecutorService;

public interface BarrageSessionFactoryBuilder {
    BarrageSessionFactoryBuilder managedChannel(ManagedChannel channel);

    BarrageSessionFactoryBuilder scheduler(ScheduledExecutorService scheduler);

    BarrageSessionFactoryBuilder allocator(BufferAllocator bufferAllocator);

    BarrageSessionFactory build();
}
