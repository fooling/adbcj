package org.adbcj.h2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.adbcj.h2.packets.ClientToServerPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;


class Encoder extends MessageToByteEncoder<ClientToServerPacket> {
    private final static Logger logger = LoggerFactory.getLogger(Encoder.class);


    @Override
    public void encode(ChannelHandlerContext ctx, ClientToServerPacket request, ByteBuf buffer) throws Exception {
    	final int wi = buffer.writerIndex();
        ByteBufOutputStream out = new ByteBufOutputStream(buffer);
        DataOutputStream dataOutputStream = new DataOutputStream(out);
        request.writeToStream(dataOutputStream);
        dataOutputStream.close();
        out.close();
        // debug packet sent since 2017-09-19 pzp
        if(logger.isDebugEnabled()) {
        	final int length = buffer.writerIndex() - wi;
        	logger.debug("Packet sent: request - {}\n{}", request, ByteBufUtil.prettyHexDump(buffer, wi, length));
    	}
    }
}
