#netty中的channel状态迁移
如我们所知，netty中的channel其实是和Java的nioChannel一一对应的，而这个又是和操作系统底层的tcp（暂时不考虑udp）实现一一对应的，那么我们就产生了一个疑问，netty中的channel的状态和tcp的状态是怎样对应起来的呢？下面我们通过debug和抓包的方式来获得答案。

---
首先，第一个问题是，netty的channel究竟有哪些状态：
我在这里分为了两个维度，一个是靠近底层上的：就是四种：
**OP_READ（表示可读），OP_WRITE（可写），OP_CONNECT（已连接），OP_ACCEPT（已接收）**，
这四种状态其实就是Java的nio所定义的四种selectkey所感兴趣的事件。

另一种，则是应用层上的，也就是我们所使用netty的时候，netty提供给我们的状态。有
registered，channelactive，channelinactive,

---
我们知道netty在处理连接的时候是用的reactor模型，也就是boss线程只负责连接的管理，而后续的io读写都交给worker线程。
##客户端channel状态分析
首先以一个发起连接的client为例，客户端通常来说，是负责主动发起连接的一方，因此，他不存在等待连接进入的问题，
而且作为发起者这些都是可控的，因此nettty对于客户端并没有使用一个统一的reactor来分发连接，而是通常设置了一个eventloopgroup，每次通过next（）方法选择线程去发起连接，个人觉得这个reactor模式下的boss线程很大意义上就是起到了一个类似于Nginx一样的功能，而客户端由于是连接的发起者，因此可以在自身发起连接前就把这种负载均衡的机制给实现了。



那么从客户端发起连接开始分析：

1.经过一些参数校验之后，会进入到`io.netty.bootstrap.Bootstrap#doResolveAndConnect`方法中，这个方法的核心就是两点：

a.`io.netty.bootstrap.AbstractBootstrap#initAndRegister`;

b.`doResolveAndConnect0(channel, remoteAddress, localAddress, channel.newPromise())`;


接下来，开始分析`io.netty.bootstrap.AbstractBootstrap#initAndRegister`
如下

```java

final ChannelFuture initAndRegister() {
        Channel channel = null;
        try {
            //这里使用了典型的工厂模式，根据传入的class来生产出对应的实例;
            channel = channelFactory.newChannel();
            init(channel);
        } catch (Throwable t) {
            if (channel != null) {
                // channel can be null if newChannel crashed (eg SocketException("too many open files"))
                channel.unsafe().closeForcibly();
            }
            // as the Channel is not registered yet we need to force the usage of the GlobalEventExecutor
            return new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE).setFailure(t);
        }

        ChannelFuture regFuture = config().group().register(channel);
        if (regFuture.cause() != null) {
            if (channel.isRegistered()) {
                channel.close();
            } else {
                channel.unsafe().closeForcibly();
            }
        }

        return regFuture;
    }
```
**这个方法无论是bind还是connect都是会先进入到这个方法里面的。**

接下来进入`init(channel)`

```java

void init(Channel channel) throws Exception {
        ChannelPipeline p = channel.pipeline();
        
        p.addLast(config.handler());

        final Map<ChannelOption<?>, Object> options = options0();
        synchronized (options) {
            setChannelOptions(channel, options, logger);
        }

        final Map<AttributeKey<?>, Object> attrs = attrs0();
        synchronized (attrs) {
            for (Entry<AttributeKey<?>, Object> e: attrs.entrySet()) {
                channel.attr((AttributeKey<Object>) e.getKey()).set(e.getValue());
            }
        }
    }
    
```
这里就是把之前定义的handler给加在了pipeline上，这里的handler通常都是我们所实现的匿名内部类`ChannelInitializer`.
然后把一些options（比如各种tcp参数）和attr（一个类似于threadlocal的东西，跟channel所绑定的一些变量）设置到channel上。

然后是注册，如果我们设置的bootstrap的eventloopgroup是多线程的，那么就会进入如下方法：

```java

public ChannelFuture register(Channel channel) {
        return next().register(channel);
    }
```
其实就在线程组中分配一个线程，这里是用的很简单的顺序分配的方式;
然后进入：
```java

@Override
    public ChannelFuture register(Channel channel) {
        return register(new DefaultChannelPromise(channel, this));
    }
```
这个promise是继承future的，在future的基础上，可以标记成功啥的，这里可以看到这个future是跟channel和线程（nioEventLoop）绑定的。

然后进入`io.netty.channel.AbstractChannel.AbstractUnsafe.register`方法中：

```java

if (eventLoop.inEventLoop()) {
                register0(promise);
            } else {
                try {
                    eventLoop.execute(new Runnable() {
                        @Override
                        public void run() {
                            register0(promise);
                        }
                    });
                } catch (Throwable t) {
                    logger.warn(
                            "Force-closing a channel whose registration task was not accepted by an event loop: {}",
                            AbstractChannel.this, t);
                    closeForcibly();
                    closeFuture.setClosed();
                    safeSetFailure(promise, t);
                }
            }
```
经典的inEventLoop()用法，先判断在不在inEventLoop中，然后如果不在的话，就作为一个任务丢到任务队列里去；
通常来说，到这一步的线程往往是main线程，所以是会进入到else中去的。

我们来看下这个execute方法：
```java

@Override
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }

        boolean inEventLoop = inEventLoop();
        if (inEventLoop) {
            addTask(task);
        } else {
            startThread();
            addTask(task);
            if (isShutdown() && removeTask(task)) {
                reject();
            }
        }

        if (!addTaskWakesUp && wakesUpForTask(task)) {
            wakeup(inEventLoop);
        }
    }
```
如果这个eventloop还没有启动过，那就先start：
```java

private void doStartThread() {
        assert thread == null;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                thread = Thread.currentThread();
                if (interrupted) {
                    thread.interrupt();
                }

                boolean success = false;
                updateLastExecutionTime();
                try {
                    SingleThreadEventExecutor.this.run();
                    success = true;
                } catch (Throwable t) {
                    logger.warn("Unexpected exception from an event executor: ", t);
                } finally {
                    for (;;) {
                        int oldState = state;
                        if (oldState >= ST_SHUTTING_DOWN || STATE_UPDATER.compareAndSet(
                                SingleThreadEventExecutor.this, oldState, ST_SHUTTING_DOWN)) {
                            break;
                        }
                    }

                    // Check if confirmShutdown() was called at the end of the loop.
                    if (success && gracefulShutdownStartTime == 0) {
                        logger.error("Buggy " + EventExecutor.class.getSimpleName() + " implementation; " +
                                SingleThreadEventExecutor.class.getSimpleName() + ".confirmShutdown() must be called " +
                                "before run() implementation terminates.");
                    }

                    try {
                        // Run all remaining tasks and shutdown hooks.
                        for (;;) {
                            if (confirmShutdown()) {
                                break;
                            }
                        }
                    } finally {
                        try {
                            cleanup();
                        } finally {
                            STATE_UPDATER.set(SingleThreadEventExecutor.this, ST_TERMINATED);
                            threadLock.release();
                            if (!taskQueue.isEmpty()) {
                                logger.warn(
                                        "An event executor terminated with " +
                                                "non-empty task queue (" + taskQueue.size() + ')');
                            }

                            terminationFuture.setSuccess(null);
                        }
                    }
                }
            }
        });
    }
```
注意这里在excute的时候，就直接跳转到`io.netty.util.concurrent.ThreadPerTaskExecutor.execute`方法中，会直接生成一个新线程，然后线程start。
并最终进入了这个`io.netty.channel.nio.NioEventLoop.run()`这个方法中:

```java

protected void run() {
        for (;;) {
            try {
                switch (selectStrategy.calculateStrategy(selectNowSupplier, hasTasks())) {
                    case SelectStrategy.CONTINUE:
                        continue;
                    case SelectStrategy.SELECT:
                        select(wakenUp.getAndSet(false));

                        // 'wakenUp.compareAndSet(false, true)' is always evaluated
                        // before calling 'selector.wakeup()' to reduce the wake-up
                        // overhead. (Selector.wakeup() is an expensive operation.)
                        //
                        // However, there is a race condition in this approach.
                        // The race condition is triggered when 'wakenUp' is set to
                        // true too early.
                        //
                        // 'wakenUp' is set to true too early if:
                        // 1) Selector is waken up between 'wakenUp.set(false)' and
                        //    'selector.select(...)'. (BAD)
                        // 2) Selector is waken up between 'selector.select(...)' and
                        //    'if (wakenUp.get()) { ... }'. (OK)
                        //
                        // In the first case, 'wakenUp' is set to true and the
                        // following 'selector.select(...)' will wake up immediately.
                        // Until 'wakenUp' is set to false again in the next round,
                        // 'wakenUp.compareAndSet(false, true)' will fail, and therefore
                        // any attempt to wake up the Selector will fail, too, causing
                        // the following 'selector.select(...)' call to block
                        // unnecessarily.
                        //
                        // To fix this problem, we wake up the selector again if wakenUp
                        // is true immediately after selector.select(...).
                        // It is inefficient in that it wakes up the selector for both
                        // the first case (BAD - wake-up required) and the second case
                        // (OK - no wake-up required).

                        if (wakenUp.get()) {
                            selector.wakeup();
                        }
                        // fall through
                    default:
                }

                cancelledKeys = 0;
                needsToSelectAgain = false;
                final int ioRatio = this.ioRatio;
                if (ioRatio == 100) {
                    try {
                        processSelectedKeys();
                    } finally {
                        // Ensure we always run tasks.
                        runAllTasks();
                    }
                } else {
                    final long ioStartTime = System.nanoTime();
                    try {
                        processSelectedKeys();
                    } finally {
                        // Ensure we always run tasks.
                        final long ioTime = System.nanoTime() - ioStartTime;
                        runAllTasks(ioTime * (100 - ioRatio) / ioRatio);
                    }
                }
            } catch (Throwable t) {
                handleLoopException(t);
            }
            // Always handle shutdown even if the loop processing threw an exception.
            try {
                if (isShuttingDown()) {
                    closeAll();
                    if (confirmShutdown()) {
                        return;
                    }
                }
            } catch (Throwable t) {
                handleLoopException(t);
            }
        }
    }
```


这个方法应该说就是**最核心的处理连接**的逻辑了，这个就可以想象成传统的

```java

while(true){
    socket.accept();
}
```


下面一点点看，这个方法里最核心的有三个地方
1.select(wakenUp.getAndSet(false));
2.processSelectedKeys();
3.runAllTasks();


下面一点点来看
select：



processSelectedKeys：






runAllTasks:
还记得我们之前提到的execute方法，如果eventloop的线程还没有启动，先启动线程，那么我们现在线程启动了，会从taskqueue中取出这个task，
这个task是啥来着？
我们再重新回到：`io.netty.channel.AbstractChannel.AbstractUnsafe.register()`这个方法中
```java
public final void register(EventLoop eventLoop, final ChannelPromise promise) {
            if (eventLoop == null) {
                throw new NullPointerException("eventLoop");
            }
            if (isRegistered()) {
                promise.setFailure(new IllegalStateException("registered to an event loop already"));
                return;
            }
            if (!isCompatible(eventLoop)) {
                promise.setFailure(
                        new IllegalStateException("incompatible event loop type: " + eventLoop.getClass().getName()));
                return;
            }

            AbstractChannel.this.eventLoop = eventLoop;

            if (eventLoop.inEventLoop()) {
                register0(promise);
            } else {
                try {
                    eventLoop.execute(new Runnable() {
                        @Override
                        public void run() {
                            register0(promise);
                        }
                    });
                } catch (Throwable t) {
                    logger.warn(
                            "Force-closing a channel whose registration task was not accepted by an event loop: {}",
                            AbstractChannel.this, t);
                    closeForcibly();
                    closeFuture.setClosed();
                    safeSetFailure(promise, t);
                }
            }
        }
```
对了，就是`register0(promise)`这个方法，下面我们看下这个方法到底执行了什么：
```java

private void register0(ChannelPromise promise) {
            try {
                // check if the channel is still open as it could be closed in the mean time when the register
                // call was outside of the eventLoop
                if (!promise.setUncancellable() || !ensureOpen(promise)) {
                    return;
                }
                boolean firstRegistration = neverRegistered;
                doRegister();
                neverRegistered = false;
                registered = true;

                // Ensure we call handlerAdded(...) before we actually notify the promise. This is needed as the
                // user may already fire events through the pipeline in the ChannelFutureListener.
                pipeline.invokeHandlerAddedIfNeeded();

                safeSetSuccess(promise);
                pipeline.fireChannelRegistered();
                // Only fire a channelActive if the channel has never been registered. This prevents firing
                // multiple channel actives if the channel is deregistered and re-registered.
                if (isActive()) {
                    if (firstRegistration) {
                        pipeline.fireChannelActive();
                    } else if (config().isAutoRead()) {
                        // This channel was registered before and autoRead() is set. This means we need to begin read
                        // again so that we process inbound data.
                        //
                        // See https://github.com/netty/netty/issues/4805
                        beginRead();
                    }
                }
            } catch (Throwable t) {
                // Close the channel directly to avoid FD leak.
                closeForcibly();
                closeFuture.setClosed();
                safeSetFailure(promise, t);
            }
        }
```

这个方法也可以分成四个部分：

---

doRegister();
具体如下:
```java

protected void doRegister() throws Exception {
        boolean selected = false;
        for (;;) {
            try {
                selectionKey = javaChannel().register(eventLoop().unwrappedSelector(), 0, this);
                return;
            } catch (CancelledKeyException e) {
                if (!selected) {
                    // Force the Selector to select now as the "canceled" SelectionKey may still be
                    // cached and not removed because no Select.select(..) operation was called yet.
                    eventLoop().selectNow();
                    selected = true;
                } else {
                    // We forced a select operation on the selector before but the SelectionKey is still cached
                    // for whatever reason. JDK bug ?
                    throw e;
                }
            }
        }
    }
```
其实就是eventloop封装了一下jdk的selector，然后在这里把它注册一个值等于0的key；

---

pipeline.invokeHandlerAddedIfNeeded();
这个方法很重要，我们不是会好奇，举例子来说，一般我们的客户端会这么写：
```java

bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(watchdog.handlers());
            }
        });
```
这个匿名内部类，其实就是在这里被调用的，而且这一路下来，都是在eventloop线程里被调用;可以看一下ChannelInitializer的handleradded方法的回调：

```java
public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isRegistered()) {
            // This should always be true with our current DefaultChannelPipeline implementation.
            // The good thing about calling initChannel(...) in handlerAdded(...) is that there will be no ordering
            // surprises if a ChannelInitializer will add another ChannelInitializer. This is as all handlers
            // will be added in the expected order.
            initChannel(ctx);
        }
    }
```

---

safeSetSuccess(promise);

我们在这个方法中一路debug跟进去，发现在`io.netty.bootstrap.Bootstrap.doResolveAndConnect`这个方法中注册了一个future回调，一旦注册上之后，就会执行这个operationComplete方法：
最终执行到这个方法中：
```java
doResolveAndConnect0(channel, remoteAddress, localAddress, promise);
```

**这个方法最终会给eventloop提交一个task去执行真正的connect方法。**
到这里，简单的梳理一下，在我们想要connect的时候，会先去initandregister,而register成功了以后，会调用回调方法，去真正的执行connect方法。

---

pipeline.fireChannelRegistered();

这里就是把整个pipeline中的channelRegisterd都给执行了。没啥好说的。

---
**底下的`isActive()`方法在这个步骤中是执行不到的。**

下面来看，addTask方法：

```java
final boolean offerTask(Runnable task) {
        if (isShutdown()) {
            reject();
        }
        return taskQueue.offer(task);
    }

```
最终其实走到了上面这个方法里，这里的taskQueue并不是linkedqueue,他是jtools实现的一种叫mpscqueue的数据结构
这个是一种无锁的队列，支持单个消费者和多个生产者的线程安全的队列.其实从这里也可以看出netty有很多细节的优化在不起眼的地方。


下面来看connect方法：

```java

 public ChannelFuture connect(
            final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {

        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        if (isNotValidPromise(promise, false)) {
            // cancelled
            return promise;
        }

        final AbstractChannelHandlerContext next = findContextOutbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeConnect(remoteAddress, localAddress, promise);
        } else {
            safeExecute(executor, new Runnable() {
                @Override
                public void run() {
                    next.invokeConnect(remoteAddress, localAddress, promise);
                }
            }, promise, null);
        }
        return promise;
    }
```
很显然，这里就是去找outbound的下一个，直到找到最终的head节点，然后调用它的unsafe去执行connect操作：
最终走到下面这个方法中：
```java
protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            doBind0(localAddress);
        }

        boolean success = false;
        try {
            boolean connected = SocketUtils.connect(javaChannel(), remoteAddress);
            if (!connected) {
                selectionKey().interestOps(SelectionKey.OP_CONNECT);
            }
            success = true;
            return connected;
        } finally {
            if (!success) {
                doClose();
            }
        }
    }
```
可以看到它调用了底层的socket去连接，这里通常是连接不上的，所以需要注册一个感兴趣的事件也就是SelectionKey.OP_CONNECT
**而我们通过抓包工具也可以看出，在这个socketUtils的方法执行以后，才开始有了三次握手。**
---
