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

package net.openhft.chronicle.network;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.io.Closeable;
import net.openhft.chronicle.core.threads.EventHandler;
import net.openhft.chronicle.core.threads.EventLoop;
import net.openhft.chronicle.core.threads.InvalidEventHandlerException;
import net.openhft.chronicle.core.util.ThrowingFunction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class RemoteConnector implements Closeable {
    private static final int BUFFER_SIZE = 8 << 20;

    private static final Logger LOG = LoggerFactory.getLogger(RemoteConnector.class);
    @NotNull
    private final ThrowingFunction<NetworkContext, TcpEventHandler, IOException> tcpHandlerSupplier;

    private final Integer tcpBufferSize;
    private volatile boolean closed;

    private volatile List<Closeable> closeables = new ArrayList<>();

    public RemoteConnector(@NotNull final ThrowingFunction<NetworkContext, TcpEventHandler, IOException> tcpEventHandlerFactory) {
        this.tcpBufferSize = Integer.getInteger("tcp.client.buffer.size", BUFFER_SIZE);
        this.tcpHandlerSupplier = tcpEventHandlerFactory;
    }

    private static void closeSocket(SocketChannel socketChannel) {
        Closeable.closeQuietly(socketChannel);
    }

    public void connect(final String remoteHostPort,
                        final EventLoop eventLoop,
                        @NotNull NetworkContext nc,
                        final long retryInterval) {

        final InetSocketAddress address = TCPRegistry.lookup(remoteHostPort);

        final RCEventHandler handler = new RCEventHandler(
                remoteHostPort,
                nc,
                eventLoop,
                address, retryInterval);

        eventLoop.addHandler(handler);
    }

    @Override
    public void close() {
        if (closed)
            return;

        closed = true;

        final List<Closeable> closeables = this.closeables;
        this.closeables = null;
        closeables.forEach(Closeable::closeQuietly);
    }

    private SocketChannel openSocketChannel(InetSocketAddress socketAddress) throws IOException {
        final SocketChannel result = SocketChannel.open(socketAddress);
        result.configureBlocking(false);
        Socket socket = result.socket();
        socket.setTcpNoDelay(true);
        socket.setReceiveBufferSize(tcpBufferSize);
        socket.setSendBufferSize(tcpBufferSize);
        socket.setSoTimeout(0);
        socket.setSoLinger(false, 0);
        return result;
    }

    private class RCEventHandler implements EventHandler, Closeable {

        private final InetSocketAddress address;
        private final AtomicLong nextPeriod = new AtomicLong();
        private final String remoteHostPort;
        private final NetworkContext nc;
        private final EventLoop eventLoop;
        private final long retryInterval;
        private volatile boolean closed;

        RCEventHandler(String remoteHostPort,
                       NetworkContext nc,
                       EventLoop eventLoop,
                       InetSocketAddress address, long retryInterval) {
            this.remoteHostPort = remoteHostPort;
            this.nc = nc;
            this.eventLoop = eventLoop;
            this.address = address;
            this.retryInterval = retryInterval;

        }

        @Override
        public boolean action() throws InvalidEventHandlerException, InterruptedException {
            if (closed)
                throw new InvalidEventHandlerException();
            final long time = System.currentTimeMillis();

            if (time > nextPeriod.get())
                nextPeriod.set(time + retryInterval);
            else
                return false;

            final SocketChannel sc;
            final TcpEventHandler eventHandler;

            try {
                sc = RemoteConnector.this.openSocketChannel(address);

                if (sc == null)
                    return false;

                nc.socketChannel(sc);
                nc.isAcceptor(false);

                eventHandler = tcpHandlerSupplier.apply(nc);

            } catch (AlreadyConnectedException e) {
                Jvm.debug().on(getClass(), e);
                throw new InvalidEventHandlerException();
            } catch (IOException e) {
                nextPeriod.set(System.currentTimeMillis() + retryInterval);
                return false;
            }
            eventLoop.addHandler(eventHandler);
            final List<Closeable> closeables = RemoteConnector.this.closeables;
            if (closeables == null)
                // we have died.
                Closeable.closeQuietly(eventHandler);
            else
                closeables.add(() -> closeSocket(sc));

            throw new InvalidEventHandlerException();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{"
                    + "remoteHostPort=" + remoteHostPort
                    + ", closed=" + closed +
                    "}";
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public void notifyClosing() {
            closed = true;
        }
    }
}