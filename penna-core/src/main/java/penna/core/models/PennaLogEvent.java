package penna.core.models;

import penna.core.logger.IPennaLogger;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PennaLogEvent implements LoggingEvent {

    private int cursor = 0;
    public Object[] arguments = new Object[8];
    public List<Marker> markers = new ArrayList<>();
    public List<KeyValuePair> keyValuePairs = new ArrayList<>();
    public Object extra;
    public Level level;
    public String message;
    public String threadName;
    public Throwable throwable;
    public IPennaLogger logger;
    public LogConfig config;

    /**
     * Resets all the fields that will change during log creation.
     * <br />
     * Note! ThreadName is never changing for the lifetime of the log object.
     * This is because it is bound to the object pool in the LoggingEventBuilder and,
     * therefore, will never change.
     */
    public void reset() {
        markers.clear();
        cursor = 0;
        Arrays.fill(arguments, null);
        keyValuePairs.clear();

        extra = null;
        message = null;
        throwable = null;
        logger = null;
    }

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public String getLoggerName() {
        return logger.getName();
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<Object> getArguments() {
        return Arrays.asList(arguments);
    }

    @Override
    public Object[] getArgumentArray() {
        return arguments;
    }

    public void addArgument(Object argument) {
        if (cursor + 1 > arguments.length) {
            arguments = Arrays.copyOf(arguments, arguments.length * 2);
        }
        arguments[cursor++] = argument;
    }

    public void addAllArguments(Object... newArguments) {
        if (cursor + newArguments.length > arguments.length) {
            arguments = Arrays.copyOf(arguments, arguments.length + newArguments.length * 2);
        }
        System.arraycopy(newArguments, 0, arguments, cursor, newArguments.length);
    }

    @Override
    public List<Marker> getMarkers() {
        return markers;
    }

    @Override
    public List<KeyValuePair> getKeyValuePairs() {
        return keyValuePairs;
    }

    @Override
    public Throwable getThrowable() {
        return throwable;
    }

    @Override
    public long getTimeStamp() {
        return 0;
    }

    @Override
    public String getThreadName() {
        return threadName;
    }
}