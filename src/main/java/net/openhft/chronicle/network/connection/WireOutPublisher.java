/*
 *
 *  *     Copyright (C) 2016  higherfrequencytrading.com
 *  *
 *  *     This program is free software: you can redistribute it and/or modify
 *  *     it under the terms of the GNU Lesser General Public License as published by
 *  *     the Free Software Foundation, either version 3 of the License.
 *  *
 *  *     This program is distributed in the hope that it will be useful,
 *  *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  *     GNU Lesser General Public License for more details.
 *  *
 *  *     You should have received a copy of the GNU Lesser General Public License
 *  *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package net.openhft.chronicle.network.connection;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.io.Closeable;
import net.openhft.chronicle.wire.WireType;
import net.openhft.chronicle.wire.WriteMarshallable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;

/**
 * @author Rob Austin.
 */
@FunctionalInterface
public interface WireOutPublisher extends Closeable {
    Logger LOG = LoggerFactory.getLogger(WireOutPublisher.class);

    /**
     * a static factory that creates and instance in chronicle enterprise
     *
     * @param periodMs the period between updates of the same key
     * @param delegate the  WireOutPublisher the events will get delegated to
     * @return a throttled WireOutPublisher
     */
    static WireOutPublisher newThrottledWireOutPublisher(int periodMs, @NotNull WireOutPublisher delegate) {

        try {
            final Class<?> aClass = Class.forName("software.chronicle.enterprise.throttle.ThrottledWireOutPublisher");
            final Constructor<WireOutPublisher> constructor = (Constructor) aClass.getConstructors()[0];
            return constructor.newInstance(periodMs, delegate);
        } catch (Exception e) {
            LOG.warn("To use this feature please install Chronicle-Enterprise");
            throw Jvm.rethrow(e);
        }
    }

    default void applyAction(@NotNull Bytes out) {
        throw new UnsupportedOperationException();
    }


    /**
     * @param key   the key to the event, only used when throttling, otherwise NULL if the
     *              throttling is not required
     * @param event the marshallable event
     */
    void put(@Nullable final Object key, WriteMarshallable event);

    default boolean isClosed() {
        throw new UnsupportedOperationException();
    }

    default boolean canTakeMoreData() {
        throw new UnsupportedOperationException();
    }

    default boolean isEmpty() {
        throw new UnsupportedOperationException();
    }


    @Override
    default void close() {
        throw new UnsupportedOperationException();
    }

    default void wireType(WireType wireType) {
        throw new UnsupportedOperationException();
    }

    default void clear() {
        throw new UnsupportedOperationException();
    }
}
