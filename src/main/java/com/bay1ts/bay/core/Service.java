package com.bay1ts.bay.core;

import com.bay1ts.bay.Config;
import com.bay1ts.bay.core.session.BaseSessionStore;
import com.bay1ts.bay.core.session.MemoryBasedSessionStore;
import com.bay1ts.bay.core.session.RedisBasedSessionStore;
import com.bay1ts.bay.handler.MainHandler;
import com.bay1ts.bay.handler.WebSocketServerHandler;
import com.bay1ts.bay.handler.intercepters.ChannelInterceptor;
import com.bay1ts.bay.handler.intercepters.SessionInterceptor;
import com.bay1ts.bay.route.*;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.List;


/**
 * Created by chenu on 2016/9/3.
 * 一个优化方案.当 注册 method 为get的路由时,不放到list里.因为在遍历list耗时较多.可以讲几种分别放到几个 map中.按path来找对应的 action
 */
public class Service {
    private Logger logger = LoggerFactory.getLogger(Service.class);
    private static Routes routes;
    private static StaticMatcher staticMatcher;
    private WebSocketAction webSocketAction=null;
    private String webSocketPath=null;

    public static Routes getRouterMatcher() {
        return routes;
    }

    public static StaticMatcher staticMatcher() {
        return staticMatcher;
    }

    protected Service() {
        routes = Routes.create();
        staticMatcher = new StaticMatcher();
    }

    public void listenAndStart() throws Exception {
        // 配置服务端的NIO线程组
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel ch) throws Exception {
                            if (Config.instance().isEnableHttps()){
                                SelfSignedCertificate selfSignedCertificate=new SelfSignedCertificate("localhost");

                                SslContext sslContext= SslContextBuilder.forServer(selfSignedCertificate.certificate(),selfSignedCertificate.privateKey()).build();
                                SSLEngine sslEngine=sslContext.newEngine(ch.alloc(),"localhost",Config.instance().getPort());
                                sslEngine.setUseClientMode(false);
                                ch.pipeline().addFirst("ssl",new SslHandler(sslEngine));
                            }
                            ch.pipeline().
                                    addLast("req_resp", new HttpServerCodec()).
                                    addLast("aggregator", new HttpObjectAggregator(65536)).
                                    addLast("deflater", new HttpContentCompressor(9)).
                                    addLast("streamer", new ChunkedWriteHandler());
                            if (webSocketPath != null && webSocketAction != null) {
                                ch.pipeline().
                                        addLast("something", new WebSocketServerProtocolHandler(webSocketPath)).
                                        addLast("websocket", getWebSocketServerHandler());
                            }
                            ch.pipeline().
                                    addLast("mainHandler", getMainHandler());
                        }
                    }).option(ChannelOption.SO_BACKLOG, 1024).childOption(ChannelOption.SO_KEEPALIVE, true);
            // 绑定端口，同步等待成功
            ChannelFuture f = b.bind(Config.instance().getPort()).sync();
            logger.info("Server started and listening on port " + Config.instance().getPort());
            // 等待服务端监听端口关闭
            f.channel().closeFuture().sync();
        } finally {
            // 优雅退出，释放线程池资源
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private WebSocketServerHandler getWebSocketServerHandler() {
        return new WebSocketServerHandler(this.webSocketAction);
    }

    public void webSocket(String path, WebSocketAction action) {
        this.webSocketPath = path;
        this.webSocketAction = action;
    }

    private MainHandler getMainHandler() {
        MainHandler handler = new MainHandler();
        handler
                .addInterceptor(new ChannelInterceptor())
                .addInterceptor(new SessionInterceptor(getHttpSessionStore()));


        return handler;
    }

    private BaseSessionStore getHttpSessionStore() {
        BaseSessionStore sessionStore = Config.instance().isEnableSessionStore() ? new RedisBasedSessionStore() : new MemoryBasedSessionStore();
        new Thread(new Runnable() {
            boolean watchingSession = false;

            @Override
            public void run() {
                while (!watchingSession) {
                    try {
                        sessionStore.destroyInactiveSessions();
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        logger.error("something wrong with  destroy inactive session");
                        continue;
                    }
                }
            }
        }).start();
        return sessionStore;
    }


    public void staticResources(String res) {
        staticMatcher.path(res);
    }

    public void get(final String path, final Action action) {
        addRoute(HttpMethod.get.name(), RouteImpl.create(path, action));
    }

    public void post(String path, Action action) {
        addRoute(HttpMethod.post.name(), RouteImpl.create(path, action));
    }

    public void put(String path, Action action) {
        addRoute(HttpMethod.put.name(), RouteImpl.create(path, action));
    }

    public void patch(String path, Action action) {
        addRoute(HttpMethod.patch.name(), RouteImpl.create(path, action));
    }

    public void delete(String path, Action action) {
        addRoute(HttpMethod.delete.name(), RouteImpl.create(path, action));
    }

    public void head(String path, Action action) {
        addRoute(HttpMethod.head.name(), RouteImpl.create(path, action));
    }

    public void trace(String path, Action action) {
        addRoute(HttpMethod.trace.name(), RouteImpl.create(path, action));
    }

    public void connect(String path, Action action) {
        addRoute(HttpMethod.connect.name(), RouteImpl.create(path, action));
    }

    public void options(String path, Action action) {
        addRoute(HttpMethod.options.name(), RouteImpl.create(path, action));
    }
    // TODO: 2016/10/12 像beego学习,加上any


    public void before(String path, Action action) {
        addFilter(HttpMethod.before.name(), RouteImpl.create(path, action));
    }

    private void addFilter(String httpMethod, RouteImpl filter) {
        addRoute(httpMethod, filter);
    }

    public void addRoute(String httpMethod, RouteImpl route) {
        routes.add(httpMethod, route);
        //此处有优化可能. 参看 文档注释
    }

    public void NSRoute(TreeNode... treeNodes) {
        if (treeNodes.length > 1) {
            for (TreeNode treeNode : treeNodes) {
                Iter(treeNode);
            }
        } else {
            Iter(treeNodes[0]);
        }
    }

    public TreeNode NSAdd(HttpMethod httpMethod, String path, Action action) {
        RouteEntry routeEntry = new RouteEntry(httpMethod, path, null, action);
        TreeNode treeNode = new TreeNode();
        treeNode.setObj(path);
        treeNode.setRouteEntry(routeEntry);
        treeNode.setChildList(null);
        return treeNode;
    }

    public void Iter(TreeNode treeNode) {
        if (treeNode.isLeaf()) {
            treeNode.getRouteEntry().setPath(treeNode.getPassedPath() + treeNode.getObj());
            RouteEntry routeEntry = treeNode.getRouteEntry();
            addRoute(routeEntry.getHttpMethod().name(), RouteImpl.create(routeEntry.getPath(), routeEntry.getAction()));
//            System.out.println(treeNode.getPassedPath() + treeNode.getObj());
        } else {
            List<TreeNode> list = treeNode.getChildList();
            for (TreeNode node : list) {
                node.setPassedPath(node.getParentNode().getPassedPath() + node.getParentNode().getObj().toString());
                Iter(node);
            }
        }
    }

    public TreeNode newNameSpace(String path, TreeNode... routeEntries) {
        TreeNode treeNode = new TreeNode();
        treeNode.setObj(path);
        for (TreeNode chindren : routeEntries) {
            chindren.setParentNode(treeNode);
            treeNode.addChildNode(chindren);
        }
        return treeNode;
    }

    public void halt() {
        throw new HaltException();
    }

    public void halt(int status) {
        throw new HaltException(status);
    }

    public void halt(String body) {
        throw new HaltException(body);
    }

    public void halt(int status, String body) {
        throw new HaltException(status, body);
    }

}
