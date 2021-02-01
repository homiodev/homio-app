package org.touchhome.app.camera.openhub.rtsp;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.rtsp.*;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.log4j.Log4j2;
import org.touchhome.app.camera.openhub.handler.IpCameraHandler;

import java.net.InetSocketAddress;

/**
 * The class is a WIP and not currently used, but will talk directly to RTSP and collect information about the camera and streams.
 */
@Log4j2
public class RTSPConnection {

    private Bootstrap rtspBootstrap;
    private EventLoopGroup mainEventLoopGroup = new NioEventLoopGroup();
    private IpCameraHandler ipCameraHandler;
    String username, password;

    public RTSPConnection(IpCameraHandler ipCameraHandler, String username, String password) {
        this.ipCameraHandler = ipCameraHandler;
        this.username = username;
        this.password = password;
    }

    public void connect() {
        sendRtspRequest(getRTSPoptions());
    }

    public void processMessage(Object msg) {
        log.info("reply from RTSP is {}", msg);
        if (msg.toString().contains("DESCRIBE")) {// getRTSPoptions
            // Public: OPTIONS, DESCRIBE, ANNOUNCE, SETUP, PLAY, RECORD, PAUSE, TEARDOWN, SET_PARAMETER, GET_PARAMETER
            sendRtspRequest(getRTSPdescribe());
        } else if (msg.toString().contains("CSeq: 2")) {// getRTSPdescribe
            // returns this:
            // RTSP/1.0 200 OK
            // CSeq: 2
            // x-Accept-Dynamic-Rate: 1
            // Content-Base:
            // rtsp://192.168.xx.xx:554/cam/realmonitor?channel=1&subtype=1&unicast=true&proto=Onvif/
            // Cache-Control: must-revalidate
            // Content-Length: 582
            // Content-Type: application/sdp
            sendRtspRequest(getRTSPsetup());
        } else if (msg.toString().contains("CSeq: 3")) {
            sendRtspRequest(getRTSPplay());
        }
    }

    HttpRequest getRTSPoptions() {
        HttpRequest request = new DefaultHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.OPTIONS,
                ipCameraHandler.rtspUri);
        request.headers().add(RtspHeaderNames.CSEQ, "1");
        return request;
    }

    HttpRequest getRTSPdescribe() {
        HttpRequest request = new DefaultHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.DESCRIBE,
                ipCameraHandler.rtspUri);
        request.headers().add(RtspHeaderNames.CSEQ, "2");
        return request;
    }

    HttpRequest getRTSPsetup() {
        HttpRequest request = new DefaultHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.SETUP, ipCameraHandler.rtspUri);
        request.headers().add(RtspHeaderNames.CSEQ, "3");
        request.headers().add(RtspHeaderNames.TRANSPORT, "RTP/AVP;unicast;client_port=5000-5001");
        return request;
    }

    HttpRequest getRTSPplay() {
        HttpRequest request = new DefaultHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.PLAY, ipCameraHandler.rtspUri);
        request.headers().add(RtspHeaderNames.CSEQ, "4");
        // need session to match response from getRTSPsetup()
        request.headers().add(RtspHeaderNames.SESSION, "12345678");
        return request;
    }

    private RTSPConnection getHandle() {
        return this;
    }

    @SuppressWarnings("null")
    public void sendRtspRequest(HttpRequest request) {
        if (rtspBootstrap == null) {
            rtspBootstrap = new Bootstrap();
            rtspBootstrap.group(mainEventLoopGroup);
            rtspBootstrap.channel(NioSocketChannel.class);
            rtspBootstrap.option(ChannelOption.SO_KEEPALIVE, true);
            rtspBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 4500);
            rtspBootstrap.option(ChannelOption.SO_SNDBUF, 1024 * 8);
            rtspBootstrap.option(ChannelOption.SO_RCVBUF, 1024 * 1024);
            rtspBootstrap.option(ChannelOption.TCP_NODELAY, true);
            rtspBootstrap.handler(new ChannelInitializer<SocketChannel>() {

                @Override
                public void initChannel(SocketChannel socketChannel) {
                    socketChannel.pipeline().addLast(new IdleStateHandler(18, 0, 0));
                    socketChannel.pipeline().addLast(new RtspDecoder());
                    socketChannel.pipeline().addLast(new RtspEncoder());
                    // Need to update the authhandler to work for multiple use cases, before this works.
                    // socketChannel.pipeline().addLast(new MyNettyAuthHandler(username, password, ipCameraHandler));
                    socketChannel.pipeline().addLast(new NettyRTSPHandler(getHandle()));
                }
            });
        }

        rtspBootstrap.connect(new InetSocketAddress(ipCameraHandler.getCameraEntity().getIp(), 554))
                .addListener(new ChannelFutureListener() {

                    @Override
                    public void operationComplete(ChannelFuture future) {
                        if (future == null) {
                            return;
                        }
                        if (future.isDone() && future.isSuccess()) {
                            Channel ch = future.channel();
                            ch.writeAndFlush(request);
                        } else { // an error occured
                            log.debug("Could not reach cameras rtsp on port 554.");
                        }
                    }
                });
    }
}
