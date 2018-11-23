package transport.impl;

import transport.channel.HChannel;
import transport.channel.HChannelGroup;
import transport.channel.RemoteAddress;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author yangyue
 * @Date 2018/11/14
 * @Description
 */
public class HChannelGroupImpl implements HChannelGroup {

    private int capacity;

    private volatile CopyOnWriteArrayList<HChannel> channels = new CopyOnWriteArrayList<>();

    private volatile ConcurrentLinkedDeque<HChannel> queue;


    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notifyCondition = lock.newCondition();

    private volatile int index;

    @Override
    public RemoteAddress getRemoteAddress() {
        return null;
    }

    @Override
    public HChannel next() {
        int length = channels.size();
        int i = index++ % length;
        return channels.get(i);
    }

    @Override
    public void add(HChannel h2Channel) {
        channels.add(h2Channel);
    }

    @Override
    public void remove(HChannel h2Channel) {

    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public List<? extends HChannel> getChannels() {
        return null;
    }

    @Override
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public int getCapacity() {
        return capacity;
    }
}
