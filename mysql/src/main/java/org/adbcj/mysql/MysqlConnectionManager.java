package org.adbcj.mysql;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import org.adbcj.CloseMode;
import org.adbcj.Connection;
import org.adbcj.DbCallback;
import org.adbcj.DbException;
import org.adbcj.mysql.codec.ClientRequest;
import org.adbcj.mysql.codec.MySqlClientDecoder;
import org.adbcj.mysql.codec.MySqlClientEncoder;
import org.adbcj.mysql.codec.decoding.AcceptNextResponse;
import org.adbcj.mysql.codec.decoding.Connecting;
import org.adbcj.mysql.codec.decoding.DecoderState;
import org.adbcj.support.AbstractConnectionManager;
import org.adbcj.support.ConnectionPool;
import org.adbcj.support.LoginCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MysqlConnectionManager extends AbstractConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(MysqlConnectionManager.class);


    private static final String ENCODER = MysqlConnectionManager.class.getName() + ".encoder";
    static final String DECODER = MysqlConnectionManager.class.getName() + ".decoder";
    private final LoginCredentials defaultCredentials;

    private final Bootstrap bootstrap;
    private final AtomicInteger idCounter = new AtomicInteger();
    private final NioEventLoopGroup eventLoop;

    final ConnectionPool<LoginCredentials, Channel> connectionPool;

    public MysqlConnectionManager(String host,
                                  int port,
                                  String username,
                                  String password,
                                  String schema,
                                  Map<String, String> properties) {
        super(properties);
        defaultCredentials = new LoginCredentials(username, password, schema);

        eventLoop = new NioEventLoopGroup();
        bootstrap = new Bootstrap()
                .group(eventLoop)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.AUTO_READ, false)
                .remoteAddress(new InetSocketAddress(host, port))
                .handler(new ChannelInitializer() {

                    @Override
                    public void initChannel(Channel ch) throws Exception {
                        ch.config().setAutoRead(false);
                        ch.pipeline().addLast(ENCODER, new Encoder());

                    }
                });

        if(useConnectionPool){
            this.connectionPool = new ConnectionPool<>();
        } else{
            this.connectionPool = null;
        }
    }


    @Override
    public void connect(DbCallback<Connection> connected) {
        connect(defaultCredentials.getUserName(), defaultCredentials.getPassword(), connected);
    }

    @Override
    public void connect(String user, String password, DbCallback<Connection> connected) {
        StackTraceElement[] entry = entryPointStack();
        LoginCredentials credentials = new LoginCredentials(user, password, defaultCredentials.getDatabase());

        if (isClosed()) {
            throw new DbException("Connection manager closed");
        }
        logger.debug("Starting connection");


        if(connectionPool!=null){
            Channel channel = connectionPool.tryAquire(credentials);
            if(channel!=null){
                MySqlConnection dbConn = new MySqlConnection(
                        credentials,
                        maxQueueLength(),
                        this,
                        channel,
                        getStackTracingOption()
                );

                channel.pipeline().addLast(DECODER, new Decoder(
                        new AcceptNextResponse(dbConn), dbConn));

                connected.onComplete(dbConn,
                        null);

                return;
            }
        }


        final ChannelFuture channelFuture = bootstrap.connect();

        channelFuture.addListener((ChannelFutureListener) future -> {
            logger.debug("Physical connect completed");

            Channel channel = future.channel();

            if (!future.isSuccess()) {
                if (future.cause() != null) {
                    channel.close();
                    connected.onComplete(null, DbException.wrap(future.cause(), entry));
                }
                return;
            }

            MySqlConnection connection = new MySqlConnection(
                    credentials,
                    maxQueueLength(),
                    MysqlConnectionManager.this,
                    channel,
                    getStackTracingOption());
            addConnection(connection);
            channel.pipeline().addLast(DECODER, new Decoder(
                    new Connecting(connected, entry, connection, credentials), connection));


            channel.config().setAutoRead(true);
            channel.read();
        });
    }


    @Override
    protected void doCloseConnection(Connection connection, CloseMode mode, DbCallback<Void> callback) {
        connection.close(mode,callback);
    }

    @Override
    protected void doClose(DbCallback<Void> callback, StackTraceElement[] entry) {
        new Thread("Closing MySQL ConnectionManager") {
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

    int nextId() {
        return idCounter.incrementAndGet();
    }


    void closedConnect(Connection connection) {
        removeConnection(connection);
    }
}

class Decoder extends ByteToMessageDecoder {
    private final static Logger log = LoggerFactory.getLogger(Decoder.class);
    private final MySqlClientDecoder decoder;
    private final MySqlConnection connection;

    public Decoder(DecoderState state, MySqlConnection connection) {
        decoder = new MySqlClientDecoder(state);
        this.connection = connection;
    }

    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
    	// debug buffer since 2017-10-15 little-pan
    	final boolean debug = log.isDebugEnabled();
    	if(debug) {
    		log.debug("Decoded buffer#{}: {}", buffer.hashCode(), buffer);
    	}
        final InputStream in = new ByteBufInputStream(buffer);
        try {
            Object obj = decoder.decode(in, ctx.channel(), false);
            if (log.isDebugEnabled() && null != obj) {
                log.debug("Decoded message: {}", obj);
            }
            if (obj != null) {
                out.add(obj);
            }
        } finally {
            in.close();
        }
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        connection.tryCompleteClose(null);
    }
}


@ChannelHandler.Sharable
class Encoder extends MessageToByteEncoder<ClientRequest> {
    private final static Logger log = LoggerFactory.getLogger(Encoder.class);

    private final MySqlClientEncoder encoder = new MySqlClientEncoder();

    @Override
    public void encode(ChannelHandlerContext ctx, ClientRequest msg, ByteBuf buffer) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Sending request: {}", msg);
        }

        ByteBufOutputStream out = new ByteBufOutputStream(buffer);
        encoder.encode(msg, out);
        out.close();
    }
}

