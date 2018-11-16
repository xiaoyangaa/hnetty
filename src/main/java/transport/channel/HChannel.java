package transport.channel;

import io.netty.channel.Channel;

/**
 * @author yangyue
 * @Date 2018/11/14
 * @Description
 */
public class HChannel {

    private Channel channel;

    private String streamId;

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }
}
