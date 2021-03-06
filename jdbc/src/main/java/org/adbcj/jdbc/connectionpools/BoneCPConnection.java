package org.adbcj.jdbc.connectionpools;

import com.jolbox.bonecp.BoneCP;
import org.adbcj.jdbc.JDBCConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author roman.stoffel@gamlor.info
 * @since 28.04.12
 */
public class BoneCPConnection implements JDBCConnectionProvider {
    private final BoneCP connectionPool;

    public BoneCPConnection(BoneCP connectionPool) {
        this.connectionPool = connectionPool;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connectionPool.getConnection();
    }
}
