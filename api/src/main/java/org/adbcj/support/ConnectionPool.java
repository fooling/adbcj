package org.adbcj.support;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;


public final class ConnectionPool<TKey, VConnection> {

    private ConcurrentMap<TKey, ConcurrentLinkedDeque<VConnection>> keyToConnections = new ConcurrentHashMap<>();

    public VConnection tryAquire(TKey key){
        ConcurrentLinkedDeque<VConnection> connections = keyToConnections.get(key);
        if(connections==null){
            return null;
        }
        return connections.pollFirst();
    }

    public void release(TKey key, VConnection connection){
        if(key==null){
            throw new IllegalArgumentException("key cannot be null");
        }
        if(connection==null){
            throw new IllegalArgumentException("connection cannot be null");
        }

        ConcurrentLinkedDeque<VConnection> connections = keyToConnections.get(key);
        if(null==connections){
            keyToConnections.putIfAbsent(key, new ConcurrentLinkedDeque<>());
            connections = keyToConnections.get(key);
        }

        connections.offer(connection);
    }
}
