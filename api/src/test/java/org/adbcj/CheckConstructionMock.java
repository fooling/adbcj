package org.adbcj;

import org.adbcj.support.ConnectionManagerFactory;

import java.util.Map;


public class CheckConstructionMock implements ConnectionManagerFactory {
    private static ThreadLocal<CheckConstructionManager> lastInstance = new ThreadLocal<CheckConstructionManager>();

    @Override
    public ConnectionManager createConnectionManager(String url, String username,
                                                     String password,
                                                     Map<String, String> properties) throws DbException {
        CheckConstructionManager instance = new CheckConstructionManager(url,username,password,properties);
        lastInstance.set(instance);
        return instance;
    }



    public static CheckConstructionManager lastInstanceRequestedOnThisThread(){
        return lastInstance.get();
    }
    @Override
    public boolean canHandle(String protocol) {
        return "apimock".equals(protocol);
    }
}
