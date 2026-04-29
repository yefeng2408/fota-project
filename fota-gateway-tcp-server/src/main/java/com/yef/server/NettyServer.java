package com.yef.server;

import com.yef.config.FotaServerChannelInitializer;
import com.yef.session.SessionManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.NettyRuntime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NettyServer implements SmartLifecycle {

    private final int configuredPort;
    private volatile boolean running;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private final SessionManager sessionManager;
    private final FotaServerChannelInitializer channelInitializer;

    public NettyServer(@Value("${netty.server.port:7611}") int configuredPort,
                       SessionManager sessionManager,
                       FotaServerChannelInitializer channelInitializer) {
        this.configuredPort = configuredPort;
        this.sessionManager = sessionManager;
        this.channelInitializer = channelInitializer;
    }

    @Override
    public synchronized void start() {

        if (running) {
            return;
        }
        Class<? extends ServerChannel> serverChannelClass;
        if(Epoll.isAvailable()){
            bossGroup = new EpollEventLoopGroup(1);
            workerGroup = new EpollEventLoopGroup(NettyRuntime.availableProcessors()*2);
            serverChannelClass = EpollServerSocketChannel.class;
        }else if (KQueue.isAvailable()){
            bossGroup = new KQueueEventLoopGroup(1);
            workerGroup = new KQueueEventLoopGroup(NettyRuntime.availableProcessors()*2);
            serverChannelClass = KQueueServerSocketChannel.class;
        }else {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup(NettyRuntime.availableProcessors()*2);
            serverChannelClass = NioServerSocketChannel.class;
        }

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();

            bootstrap.group(bossGroup, workerGroup)
                    .channel(serverChannelClass)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(channelInitializer);

            System.out.println("Netty server starting on port: " + configuredPort);

            ChannelFuture future = bootstrap.bind(configuredPort).sync();
            serverChannel = future.channel();
            running = true;
            System.out.println("Netty server started. listen port = " + getBoundPort());

        } catch (Exception e) {
            shutdownGroups();
            throw new RuntimeException("failed to start Netty server", e);
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (serverChannel != null) {
            serverChannel.close();
            serverChannel = null;
        }
        shutdownGroups();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    public int getBoundPort() {
        if (serverChannel == null) {
            return configuredPort;
        }
        return ((java.net.InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    private void shutdownGroups() {
        System.out.println("Netty server shutting down...");
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
            workerGroup = null;
        }
    }
}
