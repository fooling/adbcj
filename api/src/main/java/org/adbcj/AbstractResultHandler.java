package org.adbcj;

/**
 * An empty implementation of the {@link ResultHandler} interface to avoid
 * implementing each and every method.
 */
public abstract class AbstractResultHandler<T> implements ResultHandler<T> {
    @Override
    public void startFields(T accumulator) {
    }

    @Override
    public void field(Field field, T accumulator) {
    }

    @Override
    public void endFields(T accumulator) {
    }

    @Override
    public void startResults(T accumulator) {
    }

    @Override
    public void startRow(T accumulator) {
    }

    @Override
    public void value(Value value, T accumulator) {
    }

    @Override
    public void endRow(T accumulator) {
    }

    @Override
    public void endResults(T accumulator) {
    }
}
