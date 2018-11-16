package transport.channel;

import java.util.List;

/**
 * @author yangyue
 * @Date 2018/11/14
 * @Description
 */
public interface HChannelGroup {

    RemoteAddress getRemoteAddress();

    HChannel next();

    void add(HChannel h2Channel);

    void remove(HChannel h2Channel);

    int size();

    boolean isEmpty();

    List<? extends HChannel> getChannels();

    void setCapacity(int capacity);

    int getCapacity();
}
