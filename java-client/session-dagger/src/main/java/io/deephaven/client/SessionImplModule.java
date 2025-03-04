/**
 * Copyright (c) 2016-2022 Deephaven Data Labs and Patent Pending
 */
package io.deephaven.client;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import io.deephaven.client.impl.SessionImpl;
import io.deephaven.client.impl.SessionImplConfig;
import io.deephaven.proto.DeephavenChannel;
import io.deephaven.proto.DeephavenChannelImpl;
import io.grpc.Channel;
import io.grpc.ManagedChannel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

@Module
public interface SessionImplModule {

    @Binds
    Channel bindsManagedChannel(ManagedChannel managedChannel);

    @Binds
    DeephavenChannel bindsDeephavenChannelImpl(DeephavenChannelImpl deephavenChannelImpl);

    @Provides
    static SessionImpl session(DeephavenChannel channel, ScheduledExecutorService scheduler) {
        return SessionImplConfig.builder()
                .executor(scheduler)
                .channel(channel)
                .build()
                .createSession();
    }

    @Provides
    static CompletableFuture<? extends SessionImpl> sessionFuture(DeephavenChannel channel,
            ScheduledExecutorService scheduler) {
        return SessionImplConfig.builder()
                .executor(scheduler)
                .channel(channel)
                .build()
                .createSessionFuture();
    }
}
