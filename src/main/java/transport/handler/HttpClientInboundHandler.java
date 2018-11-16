package transport.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import transport.channel.HRequest;
import transport.channel.RemoteAddress;

import java.net.URI;
import java.util.Date;

public class HttpClientInboundHandler extends SimpleChannelInboundHandler {

    private static final int HTTP_SUCESSS = 200;


    private static final ByteBuf HEARTBEAT_SEQUENCE = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("Heartbeat", CharsetUtil.UTF_8));

    private static final int TRY_TIMES = 20;

    private int currentTime = 0;

    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;
            HttpResponseStatus status = response.status();
            if (status.code() == HTTP_SUCESSS) {
                System.out.println("request success");
            } else {
                System.out.println("request failed");
            }
            System.out.println("CONTENT_TYPE:" + response.headers().get(HttpHeaders.Names.CONTENT_TYPE));
        }
        if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent) msg;
            ByteBuf buf = content.content();
            System.out.println(buf.toString(io.netty.util.CharsetUtil.UTF_8));
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("激活时间是：" + new Date());
        System.out.println("HeartBeatClientHandler channelActive");
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("停止时间是：" + new Date());
        System.out.println("HeartBeatClientHandler channelInactive");
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        System.out.println("循环触发时间：" + new Date());
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                if (currentTime <= TRY_TIMES) {
                    System.out.println("currentTime:" + currentTime);
                    currentTime++;
                    HRequest hRequest = buildCheckAliveRequest(currentTime);
                    ctx.channel().writeAndFlush(hRequest.getRequest());
                }
            }
        }
    }

    private static HRequest buildCheckAliveRequest(int i) throws Exception {
        URI uri = new URI("http://127.0.0.1:80/check.do");
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.toASCIIString());
        // 构建http请求
        request.headers().set(HttpHeaders.Names.HOST, "http://127.0.0.1");
        request.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        request.headers().set(HttpHeaders.Names.CONTENT_LENGTH, request.content().readableBytes());

        HRequest hRequest = new HRequest();
        hRequest.setRemoteAddress(new RemoteAddress("127.0.0.1", 80));
        hRequest.setRequest(request);
        hRequest.setRequestId(i);
        return hRequest;
    }
}