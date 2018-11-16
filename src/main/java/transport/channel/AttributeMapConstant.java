package transport.channel;

import io.netty.util.AttributeKey;

public class AttributeMapConstant {

    public static final AttributeKey<Integer> HCHANNEL_ID = AttributeKey.valueOf("hchannelId");
    public static final AttributeKey<Long> START_TIME = AttributeKey.valueOf("requestStartTime");
    public static final AttributeKey<Long> END_TIME = AttributeKey.valueOf("requestEndTime");

}