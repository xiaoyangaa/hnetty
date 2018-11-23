package transport.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import transport.HConnector;
import transport.channel.HChannel;
import transport.channel.HChannelGroup;
import transport.channel.RemoteAddress;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;

import static util.Preconditions.checkNotNull;

/**
 * @author yangyue
 * @Date 2018/11/14
 * @Description
 */
public abstract class AbstractHConnector<T> implements HConnector<T> {

    private Bootstrap bootstrap;
    private EventLoopGroup worker;
    private EventLoopGroup boss;
    private int nWorkers;
    private CopyOnWriteArrayList<HChannelGroup> hChannelGroups;
    private volatile int maxConnection;
    private List<RemoteAddress> remoteAddressList;
    private volatile int maxConnectionPerRoute;


    private static final int DEFAULT_CAPACITY_GROUP = 100;
    private volatile ConcurrentHashMap<RemoteAddress, HChannelGroup> groupMap = new ConcurrentHashMap<>();

    public AbstractHConnector(int nWorkers, int maxConnectionPerRoute, int maxConnection, List<RemoteAddress> remoteAddressList) {
        this.nWorkers = nWorkers;
        this.maxConnection = maxConnection;
        this.maxConnectionPerRoute = maxConnectionPerRoute;
        this.remoteAddressList = remoteAddressList;
    }

    @Override
    public HChannel dispatch(RemoteAddress remoteAddress) {
        HChannelGroup selectedGroup = getGroup(remoteAddress);
        checkNotNull(selectedGroup);
        return select(selectedGroup);
    }

    protected HChannel select(HChannelGroup selectedGroup) {
        return selectedGroup.next();
    }

    public HChannelGroup getGroup(RemoteAddress remoteAddress) {
        return groupMap.get(remoteAddress);
    }


    @Override
    public void init() {
        ThreadFactory workerFactory = workerThreadFactory("hnetty.connector");
        worker = initEventLoopGroup(nWorkers, workerFactory);
        bootstrap = new Bootstrap().group(worker);

        bootstrap.channel(NioSocketChannel.class);
        hChannelGroups = new CopyOnWriteArrayList();

        initConfig();
        for (RemoteAddress remoteAddress : remoteAddressList) {
            HChannelGroup hChannelGroup = new HChannelGroupImpl();
            hChannelGroup.setCapacity(DEFAULT_CAPACITY_GROUP);
            hChannelGroups.add(hChannelGroup);
            groupMap.put(remoteAddress, hChannelGroup);
        }
        doConnect();

    }

    private void doConnect() {
        for (RemoteAddress remoteAddress : remoteAddressList) {
            for (int i = 0; i < maxConnectionPerRoute; i++) {
                connect(remoteAddress);
            }
        }
    }

    protected abstract void initConfig();

    protected abstract EventLoopGroup initEventLoopGroup(int nWorkers, ThreadFactory workerFactory);


    private ThreadFactory workerThreadFactory(String name) {
        return new DefaultThreadFactory(name, Thread.MAX_PRIORITY);
    }

    @Override
    public void setMaxConnection(int maxConnection) {
        this.maxConnection = maxConnection;
    }

    @Override
    public void addH2ChannelGroup(HChannelGroup h2ChannelGroup) {

    }

    @Override
    public void removeH2ChanneGroup(HChannelGroup h2ChannelGroup) {

    }

    @Override
    public Collection<HChannelGroup> groups() {
        return hChannelGroups;
    }


    @Override
    public void shutdownGracefully() {

    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public List<RemoteAddress> getRemoteAddressList() {
        return remoteAddressList;
    }

    public void setRemoteAddressList(List<RemoteAddress> remoteAddressList) {
        this.remoteAddressList = remoteAddressList;
    }
}
