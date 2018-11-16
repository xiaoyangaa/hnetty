/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package transport.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import transport.channel.HChannel;
import transport.channel.HChannelGroup;
import transport.channel.RemoteAddress;

import java.util.concurrent.TimeUnit;

/**
 * Connections watchdog.
 * <p>
 * jupiter
 * org.jupiter.transport.netty.handler.connector
 *
 * @author jiachun.fjc
 */
@ChannelHandler.Sharable
public abstract class ConnectionWatchdog extends ChannelInboundHandlerAdapter implements TimerTask, ChannelHandlerHolder {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionWatchdog.class);

    private static final int ST_STARTED = 1;
    private static final int ST_STOPPED = 2;

    private final Bootstrap bootstrap;
    private final Timer timer;
    private final RemoteAddress remoteAddress;
    private final HChannelGroup group;

    private volatile int state = ST_STARTED;
    private int attempts;

    public ConnectionWatchdog(Bootstrap bootstrap, Timer timer, RemoteAddress remoteAddress, HChannelGroup group) {
        this.bootstrap = bootstrap;
        this.timer = timer;
        this.remoteAddress = remoteAddress;
        this.group = group;
    }

    public boolean isStarted() {
        return state == ST_STARTED;
    }

    public void start() {
        state = ST_STARTED;
    }

    public void stop() {
        state = ST_STOPPED;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();

        if (group != null) {
            HChannel hChannel = new HChannel();
            hChannel.setChannel(ch);
            group.add(hChannel);
        }

        attempts = 0;

        logger.info("Connects with {}.", ch);

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        boolean doReconnect = isReconnectNeeded();
        if (doReconnect) {
            if (attempts < 12) {
                attempts++;
            }
            long timeout = 2 << attempts;
            timer.newTimeout(this, timeout, TimeUnit.MILLISECONDS);
        }
        logger.warn("Disconnects with {}, address: {}", ctx.channel(), remoteAddress);

        ctx.fireChannelInactive();
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        if (!isReconnectNeeded()) {
            logger.warn("Cancel reconnecting with {}.", remoteAddress);
            return;
        }

        ChannelFuture future;
        synchronized (bootstrap) {
            bootstrap.handler(new ChannelInitializer<Channel>() {

                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(handlers());
                }
            });
            future = bootstrap.connect(remoteAddress.getHost(), remoteAddress.getPort());
        }

        future.addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture f) throws Exception {
                boolean succeed = f.isSuccess();
                logger.warn("Reconnects with {}, {}.", remoteAddress, succeed ? "succeed" : "failed");
                if (!succeed) {
                    f.channel().pipeline().fireChannelInactive();
                }
            }
        });
    }

    private boolean isReconnectNeeded() {
        return isStarted() && (group == null || (group.size() < group.getCapacity()));
    }
}
