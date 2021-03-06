/*
 * Copyright 2016 higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.network.cluster;

import net.openhft.chronicle.core.annotation.Nullable;
import net.openhft.chronicle.core.io.Closeable;
import net.openhft.chronicle.wire.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * @author Rob Austin.
 */
abstract public class Cluster<E extends HostDetails, C extends ClusterContext> implements Marshallable,
        Closeable {

    private final Map<String, E> hostDetails;
    private final String clusterName;

    private C clusterContext;

    public Cluster(String clusterName) {
        hostDetails = new ConcurrentSkipListMap<>();
        this.clusterName = clusterName;
    }

    protected C clusterContext() {
        return (C) clusterContext;
    }

    @Override
    public void readMarshallable(@NotNull WireIn wire) throws IllegalStateException {

        hostDetails.clear();

        if (!wire.hasMore())
            return;
        while (wire.hasMore()) {

            StringBuilder sb = Wires.acquireStringBuilder();

            ValueIn valueIn = wire.readEventName(sb);

            if ("context".contentEquals(sb)) {
                clusterContext = (C) valueIn.typedMarshallable();
                clusterContext.clusterName(clusterName);
                continue;
            }

            valueIn.marshallable(details -> {
                final E hd = newHostDetails();
                hd.readMarshallable(details);
                hostDetails.put(sb.toString(), hd);
            });

        }

        if (clusterContext == null)
            throw new IllegalStateException("required field 'context' is missing.");

    }

    @Nullable
    private HostDetails findHostDetails(int remoteIdentifier) {

        for (HostDetails hd : hostDetails.values()) {
            if (hd.hostId() == remoteIdentifier)
                return hd;
        }
        return null;
    }

    public <H extends HostDetails, C extends ClusterContext> ConnectionStrategy
    findConnectionStrategy(int remoteIdentifier) {

        HostDetails hostDetails = findHostDetails(remoteIdentifier);
        if (hostDetails == null) return null;
        return hostDetails.connectionStrategy();
    }

    public ConnectionManager findConnectionManager(int remoteIdentifier) {
        HostDetails hostDetails = findHostDetails(remoteIdentifier);
        if (hostDetails == null) return null;
        return hostDetails.connectionManager();
    }

    public TerminationEventHandler findTerminationEventHandler(int remoteIdentifier) {
        HostDetails hostDetails = findHostDetails(remoteIdentifier);
        if (hostDetails == null) return null;
        return hostDetails.terminationEventHandler();

    }

    public ConnectionChangedNotifier findClusterNotifier(int remoteIdentifier) {
        HostDetails hostDetails = findHostDetails(remoteIdentifier);
        if (hostDetails == null) return null;
        return hostDetails.clusterNotifier();
    }

    abstract protected E newHostDetails();

    @Override
    public void writeMarshallable(@NotNull WireOut wire) {
        for (Map.Entry<String, E> entry2 : hostDetails.entrySet()) {
            wire.writeEventName(entry2::getKey).marshallable(entry2.getValue());
        }
    }

    @NotNull
    public Collection<E> hostDetails() {
        return hostDetails.values();
    }

    @Override
    public void close() {
        hostDetails().forEach(Closeable::closeQuietly);
    }

    public void install() {
        if (clusterContext != null && hostDetails != null && hostDetails.values() != null)
            hostDetails.values().forEach(clusterContext::accept);
    }
}
