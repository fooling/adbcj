package org.adbcj.support;


public interface OneArgFunction<TArgument, TReturn> {
    TReturn apply(TArgument arg);

    OneArgFunction ID_FUNCTION = arg -> arg;
}


