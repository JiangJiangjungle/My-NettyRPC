package com.jsj.rpc.server;

import com.jsj.rpc.NamedThreadFactory;
import com.jsj.rpc.RpcService;
import com.jsj.rpc.codec.CodeC;
import com.jsj.rpc.codec.DefaultCodeC;
import com.jsj.rpc.config.DefaultServerConfiguration;
import com.jsj.rpc.registry.ServiceRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * RPC server
 *
 * @author jsj
 * @date 2018-10-8
 */
public class RpcServer implements ApplicationListener<ContextRefreshedEvent> {
    private static final String SEPARATOR = ":";
    private static final Logger LOGGER = LoggerFactory.getLogger(RpcServer.class);

    /**
     * Netty 的连接线程池
     */
    private EventLoopGroup bossGroup = new NioEventLoopGroup(1, new NamedThreadFactory(
            "Rpc-netty-server-boss", false));
    /**
     * Netty 的I/O线程池
     */
    private EventLoopGroup workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2,
            new NamedThreadFactory("Rpc-netty-server-worker", true));

    /**
     * 用户业务线程池，用于处理实际rpc业务
     */
    private ExecutorService threadPool = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 2, 60L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(1000), new NamedThreadFactory());

    /**
     * 用于存储已经注册的服务实例
     */
    public static Map<String, Object> serviceInstanceMap = new HashMap<>();

    /**
     * 编解码器
     */
    private CodeC codeC;

    /**
     * 服务端连接配置项
     */
    private DefaultServerConfiguration configuration;

    private String ip;

    private int port;

    /**
     * 服务注册中心
     */
    private ServiceRegistry serviceRegistry;

    public RpcServer(String ip, int port, ServiceRegistry serviceRegistry) {
        this(ip, port, serviceRegistry, new DefaultServerConfiguration());
    }

    public RpcServer(String ip, int port, ServiceRegistry serviceRegistry, DefaultServerConfiguration configuration) {
        this.ip = ip;
        this.port = port;
        this.serviceRegistry = serviceRegistry;
        this.configuration = configuration;
        this.codeC = DefaultCodeC.getInstance();
    }

    /**
     * 添加监听，用于扫描服务并启动server
     *
     * @throws Exception
     */

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        ApplicationContext applicationContext = contextRefreshedEvent.getApplicationContext();
        //root application context 保证只执行一次
        if (applicationContext.getParent() == null) {
            LOGGER.info("root application context 调用了onApplicationEvent！");
            //扫描带有 RpcService 注解的类并初始化 handlerMap 对象
            Map<String, Object> serviceBeanMap = applicationContext.getBeansWithAnnotation(RpcService.class);
            //注册
            this.registerAllService(serviceBeanMap);
            //启动server
            this.doRunServer();
        }
    }

    /**
     * 启动 Netty RPC服务器服务端
     */
    private void doRunServer() {
        new Thread(() -> {
            try {
                //创建并初始化 Netty 服务端辅助启动对象 ServerBootstrap
                ServerBootstrap serverBootstrap = RpcServer.this.initServerBootstrap(bossGroup, workerGroup);
                //绑定对应ip和端口，同步等待成功
                ChannelFuture future = serverBootstrap.bind(ip, port).sync();
                LOGGER.info("rpc server 已启动，端口：{}", port);
                //等待服务端监听端口关闭
                future.channel().closeFuture().sync();
            } catch (InterruptedException i) {
                LOGGER.error("rpc server 出现异常，端口：{}, cause: {}", port, i.getMessage());
            } finally {
                //优雅退出，释放 NIO 线程组
                workerGroup.shutdownGracefully();
                bossGroup.shutdownGracefully();
            }
        }, "rpc-server-thread").start();
    }

    /**
     * 注册所有服务
     */
    private void registerAllService(Map<String, Object> serviceBeanMap) {
        if (MapUtils.isEmpty(serviceBeanMap)) {
            LOGGER.debug("需要注册的 service 为空!");
        }
        RpcService rpcService;
        String serviceName;
        Object serviceBean;
        String addr = this.ip + SEPARATOR + port;
        for (Map.Entry<String, Object> entry : serviceBeanMap.entrySet()) {
            //service实例
            serviceBean = entry.getValue();
            rpcService = serviceBean.getClass().getAnnotation(RpcService.class);
            //service接口名称
            serviceName = rpcService.value().getName();
            //注册
            this.serviceRegistry.register(serviceName, addr);
            serviceInstanceMap.put(serviceName, serviceBean);
            LOGGER.debug("register service: {} => {}", serviceName, addr);
        }
    }

    /**
     * 创建并初始化 Netty 服务端辅助启动对象 ServerBootstrap
     *
     * @param bossGroup
     * @param workerGroup
     * @return
     */
    public ServerBootstrap initServerBootstrap(EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        return new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                //允许server端口reuse
                .option(ChannelOption.SO_REUSEADDR, this.configuration.getReUseAddr())
                .option(ChannelOption.SO_BACKLOG, this.configuration.getBackLog())
                .childOption(ChannelOption.SO_KEEPALIVE, this.configuration.getKeepAlive())
                .childHandler(new ServerChannelInitializer(codeC, new RpcServiceHandler(this.threadPool), this.configuration.getChannelAliveTime()));
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    public EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }
}
