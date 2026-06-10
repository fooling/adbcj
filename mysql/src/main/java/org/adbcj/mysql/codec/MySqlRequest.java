package org.adbcj.mysql.codec;

import org.adbcj.DbCallback;
import org.adbcj.mysql.codec.decoding.DecoderState;


public class MySqlRequest<T> {
    public final String description;
    public final DecoderState startState;
    public final ClientRequest request;
    public final DbCallback<T> callback;

    MySqlRequest(String description,
                 DecoderState startState,
                 ClientRequest request,
                 DbCallback<T> callback) {
        this.description = description;
        this.startState = startState;
        this.request = request;
        this.callback = callback;
    }

    @Override
    public String toString() {
        return "MySqlRequest{"+ description +  '}';
    }


}
