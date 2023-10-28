package penna.core.logger;

import penna.core.internals.Clock;
import penna.core.minilog.MiniLogger;
import penna.core.models.PennaLogEvent;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;
import org.slf4j.spi.LoggingEventBuilder;
import penna.core.sink.Sink;
import penna.core.sink.SinkManager;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

/**
 * Penna's {@link LoggingEventBuilder} implementation.
 * It relies on two major concepts:
 * a) object pooling; and
 * b) thread locals.
 * <br />
 * <strong>Object Pooling</strong>
 * Each instance of PennaLogEventBuilder will have a small pool of objects.
 * This isn't strictly necessary - we could reuse a single instance - but allows for some flexibility.
 * The main idea here is that the {@link PennaLogEventBuilder} will never create objects in runtime.
 * Instead, it will reuse existing objects in a pool. This has two main reasons:
 *  - as a logger, penna doesn't cause pressure in the GC;
 *  - given {@link PennaLogEvent}s are simple vessels for data, we can reuse them at will;
 * <br />
 * <strong>Thread Local</strong>
 * {@link PennaLogEventBuilder} has to be unique for every thread. This is because of the object pool:
 * we don't want the log object to be overwritten by another thread. Instead, what we do is we ensure
 * every thread gets one set of objects + pointers to work with, which allows us to safely control
 * the pool and ensure no two different threads are operating on the same log object.
 * That is why we can only create the builders through the static {@link Factory}.
 */
public final class PennaLogEventBuilder implements LoggingEventBuilder {
    public static final int POOL_SIZE = 16;
    public static final int POOL_SIZE_MASK = POOL_SIZE - 1;
    private final PennaLogEvent[] pool;
    private int currentIndex;
    Sink sink;
    private PennaLogEvent current;

    public static final class Factory {

        private Factory() {}

        private static final ThreadLocal<PennaLogEventBuilder> pool = ThreadLocal.withInitial(PennaLogEventBuilder::new);

        public static void fromLoggingEvent(PennaLogger logger, LoggingEvent event) {
            var builder = pool.get();
            builder.next();
            builder.setCause(event.getThrowable());
            builder.setMessage(event.getMessage());
            builder.addArguments(event.getArgumentArray());
            for (var kvp : event.getKeyValuePairs()) {
                builder.addKeyValue(kvp.key, kvp.value);
            }
            for (var marker : event.getMarkers()) {
                builder.addMarker(marker);
            }

            builder.current.level = event.getLevel();
            builder.current.logger = logger.getName().getBytes();
            builder.current.config = logger.config;
            builder.current.timestamp = Clock.getTimestamp();

            builder.log();
        }

        public static PennaLogEventBuilder get(PennaLogger logger, Level level) {
            var builder = pool.get();
            builder.next();
            builder.current.logger = logger.nameAsChars;
            builder.current.level = level;
            builder.current.config = logger.config;
            builder.current.timestamp = Clock.getTimestamp();

            return builder;
        }
    }

    /**
     * Resets the builder so the next LogEvent is ready to be used.
     */
    private void next() {
        // This is only possible because POOL_SIZE is a power of 2
        currentIndex = (currentIndex + 1) & POOL_SIZE_MASK;
        current = pool[currentIndex];
        current.reset();
    }

    private PennaLogEventBuilder() {
        String threadName = Thread.currentThread().getName();
        sink = SinkManager.Instance.get(this);
        pool = new PennaLogEvent[POOL_SIZE];
        for (int i = 0; i < POOL_SIZE; i++){
            pool[i] = new PennaLogEvent();
            pool[i].threadName = threadName.getBytes();
        }

        current = pool[currentIndex];
    }

    @Override
    public LoggingEventBuilder setCause(Throwable cause) {
        this.current.throwable = cause;
        return this;
    }

    private List<Marker> getMarkers() {
        return this.current.markers;
    }
    @Override
    public LoggingEventBuilder addMarker(Marker marker) {
        getMarkers().add(marker);
        return this;
    }

    @Override
    public LoggingEventBuilder addArgument(Object p) {
        if (p instanceof Throwable throwable) {
            setCause(throwable);
        } else {
            this.current.addArgument(p);
        }
        return this;
    }

    @Override
    public LoggingEventBuilder addArgument(Supplier<?> objectSupplier) {
        return addArgument(objectSupplier.get());
    }

    private List<KeyValuePair> getKeyValueList() {
        return this.current.keyValuePairs;
    }

    @Override
    public LoggingEventBuilder addKeyValue(String key, Object value) {
        getKeyValueList().add(new KeyValuePair(key, value));
        return this;
    }

    @Override
    public LoggingEventBuilder addKeyValue(String key, Supplier<Object> valueSupplier) {
        getKeyValueList().add(new KeyValuePair(key, valueSupplier.get()));
        return this;
    }

    @Override
    public LoggingEventBuilder setMessage(String message) {
        this.current.message = message;
        return this;
    }

    @Override
    public LoggingEventBuilder setMessage(Supplier<String> messageSupplier) {
        this.current.message = messageSupplier.get();
        return this;
    }

    @Override
    public void log() {
        try {
            sink.write(this.current);
        } catch (IOException e) {
            MiniLogger.error("Unable to write log.", e);
        }
    }

    @Override
    public void log(String message) {
        setMessage(message);
        log();
    }

    @Override
    public void log(String message, Object arg) {
        addArgument(arg);
        setMessage(message);
        log();
    }

    @Override
    public void log(String message, Object arg0, Object arg1) {
        addArgument(arg0);
        addArgument(arg1);
        setMessage(message);
        log();
    }

    @Override
    public void log(String message, Object... args) {
        addArguments(args);
        setMessage(message);
        log();
    }

    public void addArguments(Object... args) {
        this.current.addAllArguments(args);
    }

    @Override
    public void log(Supplier<String> messageSupplier) {
        setMessage(messageSupplier);
        log();
    }
}
