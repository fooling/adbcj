package org.adbcj;

public interface PreparedStatement extends AsyncCloseable{

    boolean isClosed();


    void close(DbCallback<Void> callback);
}
