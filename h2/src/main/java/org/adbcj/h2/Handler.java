package org.adbcj.h2;

import io.netty.channel.*;


class Handler  extends SimpleChannelInboundHandler<Object> {
    public Handler() {
        //To change body of created methods use File | Settings | File Templates.
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
        // We process the decodes stuff directly at the moment.
        // Not very elegant, but works
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.getCause().printStackTrace();
    }


}
