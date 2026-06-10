package org.adbcj;


import org.testng.Assert;

import java.util.Map;


public class CheckConstructionManager implements ConnectionManager{
    private final String url;
    private final String username;
    private final String password;
    private final Map<String, String> properties;
    public CheckConstructionManager(String url, String username, String password, Map<String, String> properties) {

        this.url = url;
        this.username = username;
        this.password = password;
        this.properties = properties;
    }

    public void assertURL(String url) {
        Assert.assertEquals(this.url,url);
    }
    public void assertUserName(String username) {
        Assert.assertEquals(this.username,username);
    }
    public void assertPassword(String password) {
        Assert.assertEquals(this.password,password);
    }
    public void assertProperty(String property,String value) {
        Assert.assertEquals(this.properties.get(property),value);
    }

    @Override
    public void connect(DbCallback<Connection> connected) {
        throw new Error("Mock does not support this operation");
    }

    @Override
    public void connect(String user, String password, DbCallback<Connection> connected) {
        throw new Error("Mock does not support this operation");
    }

    @Override
    public void close(DbCallback<Void> callback) {
        throw new Error("Mock does not support this operation");
    }

    @Override
    public void close(CloseMode mode, DbCallback<Void> callback) throws DbException {
        throw new Error("Mock does not support this operation");
    }


    @Override
    public boolean isClosed() {
        throw new Error("Mock does not support this operation");
    }
}
