package transport.impl;

import commons.NamedThreadFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.HashedWheelTimer;
import transport.channel.HChannelGroup;
import transport.channel.RemoteAddress;
import transport.handler.ConnectionWatchdog;
import transport.handler.HttpClientInboundHandler;

import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author yangyue
 * @Date 2018/11/15
 * @Description
 */
public class DefaultHConnector extends AbstractHConnector<NioSocketChannel> {

    protected final HashedWheelTimer timer = new HashedWheelTimer(new NamedThreadFactory("connector.timer", true));


    public DefaultHConnector(int nWorkers, int maxConnectionPerRoute, int maxConnection, List<RemoteAddress> remoteAddressList) {
        super(nWorkers, maxConnectionPerRoute, maxConnection, remoteAddressList);
    }

    @Override
    public NioSocketChannel connect(final RemoteAddress remoteAddress) {
        Bootstrap b = getBootstrap();
        ChannelFuture future = null;
        HChannelGroup group = getGroup(remoteAddress);
        final ConnectionWatchdog watchdog = new ConnectionWatchdog(b, timer, remoteAddress, group) {

            @Override
            public ChannelHandler[] handlers() {
                return new ChannelHandler[]{
                        this,
                        new IdleStateHandler(false, 0, 15, 0, TimeUnit.SECONDS),
                        // 客户端接收到的是httpResponse响应，所以要使用HttpResponseDecoder进行解码
                        new HttpResponseDecoder(),
                        // 客户端发送的是httprequest，所以要使用HttpRequestEncoder进行编码
                        new HttpRequestEncoder(),
                        new HttpClientInboundHandler()
                };
            }
        };

        b.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(watchdog.handlers());
            }
        });
        try {
            future = b.connect(remoteAddress.getHost(), remoteAddress.getPort()).sync();
        } catch (InterruptedException e) {
            future.addListener(
                    ChannelFutureListener.CLOSE
            );
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void initConfig() {
        Bootstrap bootstrap = getBootstrap();
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
    }

    @Override
    protected EventLoopGroup initEventLoopGroup(int nWorkers, ThreadFactory workerFactory) {
        return new NioEventLoopGroup(nWorkers, workerFactory);
    }
}
