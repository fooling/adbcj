package org.adbcj.support;

import org.adbcj.DbCallback;
import org.adbcj.DbException;

import java.util.ArrayList;

public final class CloseOnce {
    private final ArrayList<DbCallback<Void>> closeListeners = new ArrayList<>();
    private volatile State state = State.Open;

    public void requestClose(DbCallback<Void> callback, Runnable startCloseAction){
        synchronized (closeListeners){
            if(state==State.Closed){
                callback.onComplete(null,null);
            } else{
                boolean needStartClosing = closeListeners.isEmpty();
                closeListeners.add(callback);

                if (needStartClosing) {
                    state = State.Closing;
                    try{
                        startCloseAction.run();
                    } finally {
                        state = State.Closed;
                    }
                }
            }
        }
    }

    public void didClose(DbException failure){
        synchronized (closeListeners){
            state = State.Closed;
            for (DbCallback<Void> closeListener : closeListeners) {
                closeListener.onComplete(null,failure);
            }
            closeListeners.clear();
        }
    }

    public boolean isClose(){
        return state!=State.Open;
    }

    enum State{
        Open,
        Closing,
        Closed
    }
}
