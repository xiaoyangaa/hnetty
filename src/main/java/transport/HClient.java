package transport;

import io.netty.channel.ChannelFuture;
import transport.channel.HRequest;

/**
 * @author yangyue
 * @Date 2018/11/15
 * @Description
 */
public interface HClient {

    void init();

    ChannelFuture excute(HRequest request);

}
