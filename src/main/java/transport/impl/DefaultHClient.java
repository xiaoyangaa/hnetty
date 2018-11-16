package transport.impl;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.Attribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import transport.HClient;
import transport.HConnector;
import transport.channel.AttributeMapConstant;
import transport.channel.HChannel;
import transport.channel.HRequest;

/**
 * @author yangyue
 * @Date 2018/11/15
 * @Description
 */
public class DefaultHClient implements HClient {


    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHClient.class);

    private HConnector<NioSocketChannel> hConnector;

    public DefaultHClient(HConnector<NioSocketChannel> hConnector) {
        this.hConnector = hConnector;
    }

    @Override
    public void init() {
        hConnector.init();
    }

    @Override
    public ChannelFuture excute(HRequest hRequest) {

        HChannel hChannel = hConnector.dispatch(hRequest.getRemoteAddress());
        setAttr(hRequest, hChannel);

        ChannelFuture channelFuture = hChannel.getChannel().writeAndFlush(hRequest.getRequest());

        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                Attribute<Integer> attr = future.channel().attr(AttributeMapConstant.HCHANNEL_ID);
                Integer requestId = attr.get();
                Attribute<Long> attrStartTime = future.channel().attr(AttributeMapConstant.START_TIME);
                Long cost = System.currentTimeMillis() - attrStartTime.get();
                LOGGER.info("request:{} sended cost :{}", requestId, cost);
            }
        });
        return channelFuture;
    }

    private void setAttr(HRequest hRequest, HChannel hChannel) {
        Attribute<Integer> attrId = hChannel.getChannel().attr(AttributeMapConstant.HCHANNEL_ID);
        attrId.set(hRequest.getRequestId());
        Attribute<Long> attrStartTime = hChannel.getChannel().attr(AttributeMapConstant.START_TIME);
        attrStartTime.set(System.currentTimeMillis());
    }


    public HConnector<NioSocketChannel> gethConnector() {
        return hConnector;
    }

    public void sethConnector(HConnector<NioSocketChannel> hConnector) {
        this.hConnector = hConnector;
    }

}
