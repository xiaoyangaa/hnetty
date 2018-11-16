package transport;

import transport.channel.HChannel;
import transport.channel.HChannelGroup;
import transport.channel.RemoteAddress;

import java.util.Collection;

/**
 * @author yangyue
 * @Date 2018/11/14
 * @Description
 */
public interface HConnector<T> {

    void init();

    void setMaxConnection(int maxConnection);

    void addH2ChannelGroup(HChannelGroup h2ChannelGroup);

    void removeH2ChanneGroup(HChannelGroup h2ChannelGroup);

    Collection<HChannelGroup> groups();

    T connect(RemoteAddress remoteAddress);

    void shutdownGracefully();

    interface ConnectionWatcher {

        /**
         * Start to connect to server.
         */
        void start();

        /**
         * Wait until the connections is available or timeout,
         * if available return true, otherwise return false.
         */
        boolean waitForAvailable(long timeoutMillis);
    }


    HChannel dispatch(RemoteAddress remoteAddress);

}
