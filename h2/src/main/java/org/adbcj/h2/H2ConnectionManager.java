package org.adbcj.h2;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.adbcj.*;
import org.adbcj.h2.decoding.AnswerNextRequest;
import org.adbcj.h2.decoding.Decoder;
import org.adbcj.h2.packets.ClientHandshake;
import org.adbcj.support.AbstractConnectionManager;
import org.adbcj.support.ConnectionPool;
import org.adbcj.support.LoginCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;


public class H2ConnectionManager extends AbstractConnectionManager {
    private final static Logger logger = LoggerFactory.getLogger(H2ConnectionManager.class);

    private final Bootstrap bootstrap;
    private static final String ENCODER = H2ConnectionManager.class.getName() + ".encoder";
    static final String DECODER = H2ConnectionManager.class.getName() + ".decoder";
    private final String url;
    private final LoginCredentials defaultCredentials;
    private final Map<String, String> keys;
    private final NioEventLoopGroup eventLoop;
    final ConnectionPool<LoginCredentials, Channel> connectionPool;

    public H2ConnectionManager(String url,
                               String host,
                               int port,
                               LoginCredentials credentials,
                               Map<String, String> properties,
                               Map<String, String> keys) {
        super(properties);
        this.url = url;
        this.defaultCredentials = credentials;
        this.keys = keys;

        eventLoop = new NioEventLoopGroup();
        bootstrap = new Bootstrap()
                .group(eventLoop)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .remoteAddress(new InetSocketAddress(host, port))
                .handler(new ChannelInitializer() {

                    @Override
                    public void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(ENCODER, new Encoder());
                        ch.pipeline().addLast("handler", new Handler());
                    }
                });

        if(useConnectionPool){
            connectionPool = new ConnectionPool<>();
        } else{
            connectionPool = null;
        }
    }


    @Override
    public void connect(DbCallback<Connection> connected) {
        connect(defaultCredentials, connected);
    }

    @Override
    public final void connect(String user, String password, DbCallback<Connection> connected) {
        connect(new LoginCredentials(user, password, defaultCredentials.getDatabase()), connected);
    }

    private void connect(final LoginCredentials credentials, DbCallback<Connection> connected) {
        StackTraceElement[] entry = entryPointStack();
        if (isClosed()) {
            throw new DbConnectionClosedException("Connection manager closed");
        }
        logger.debug("Starting connection");

        if(connectionPool!=null){
            Channel channel = connectionPool.tryAquire(credentials);
            if(channel!=null){
                H2Connection dbConn = new H2Connection(
                        credentials,
                        maxQueueLength(),
                        this,
                        channel,
                        getStackTracingOption());

                channel.pipeline().addFirst(DECODER,
                        new Decoder(new AnswerNextRequest(dbConn, entry), dbConn, entry));

                connected.onComplete(dbConn,
                        null);

                return;
            }
        }

        final ChannelFuture channelFuture = bootstrap.connect();


        channelFuture.addListener((ChannelFutureListener) future -> {
            logger.debug("Connect completed");

            Channel channel = future.channel();

            if (!future.isSuccess()) {
                channel.close();
                if (future.cause() != null) {
                    connected.onComplete(null, DbException.wrap(future.cause(), entry));
                }
                return;
            }

            H2Connection connection = new H2Connection(
                    credentials,
                    maxQueueLength(),
                    H2ConnectionManager.this,
                    channel,
                    getStackTracingOption());
            channel.pipeline().addFirst(DECODER, new Decoder(connected, connection, entry));
            channel.writeAndFlush(
                    new ClientHandshake(
                            credentials.getDatabase(),
                            url,
                            credentials.getUserName(),
                            credentials.getPassword(),
                            keys));

            addConnection(connection);
        });
    }

    @Override
    protected void doCloseConnection(Connection connection, CloseMode mode, DbCallback<Void> callback) {
        connection.close(mode, callback);
    }

    @Override
    protected void doClose(DbCallback<Void> callback, StackTraceElement[] entry) {
        new Thread("Closing H2 ConnectionManager") {
            @Override
            public void run() {
                eventLoop.shutdownGracefully().addListener(future -> {

                    DbException error = null;
                    if (!future.isSuccess()) {
                        error = DbException.wrap(future.cause(), entry);
                    }
                    callback.onComplete(null, error);
                });
            }
        }.start();
    }

    void closedConnection(Connection connection){
        removeConnection(connection);
    }

}
