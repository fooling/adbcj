package info.adbcj.demo;

import org.h2.tools.Server;

public final class DemoServer {

    public static Server startServer() {

        try {
            Server server = Server.createTcpServer(
                    "-tcpAllowOthers",
                    "-tcpDaemon",
                    "-tcpPort", "14242",
                    "-baseDir", "./h2testdb");
            server.start();
            return server;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
