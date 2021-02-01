package org.touchhome.app.camera.openhub;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
import org.touchhome.app.camera.openhub.handler.IpCameraGroupHandler;
import org.touchhome.app.camera.openhub.handler.IpCameraHandler;
import org.touchhome.bundle.api.measure.OnOffType;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * responsible for handling streams and sending any requested files to Openhabs
 * features for a group of cameras instead of individual cameras.
 */
@Log4j2
public class StreamServerGroupHandler extends ChannelInboundHandlerAdapter {

    private IpCameraGroupHandler ipCameraGroupHandler;
    private String whiteList;

    public StreamServerGroupHandler(IpCameraGroupHandler ipCameraGroupHandler) {
        this.ipCameraGroupHandler = ipCameraGroupHandler;
        whiteList = ""; /*ipCameraGroupHandler.groupConfig.getIpWhitelist()*/
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
    }

    private String resolveIndexToPath(String uri) {
        if (!uri.substring(1, 2).equals("i")) {
            return ipCameraGroupHandler.getOutputFolder(Integer.parseInt(uri.substring(1, 2)));
        }
        return "notFound";
        // example is /1ipcameraxx.ts
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg == null || ctx == null) {
            return;
        }
        try {
            if (msg instanceof HttpRequest) {
                HttpRequest httpRequest = (HttpRequest) msg;
                String requestIP = "("
                        + ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress() + ")";
                if (!whiteList.contains(requestIP) && !whiteList.equals("DISABLE")) {
                    log.warn("The request made from {} was not in the whitelist and will be ignored.", requestIP);
                } else if (HttpMethod.GET.equals(httpRequest.method())) {
                    // Some browsers send a query string after the path when refreshing a picture.
                    QueryStringDecoder queryStringDecoder = new QueryStringDecoder(httpRequest.uri());
                    switch (queryStringDecoder.path()) {
                        case "/ipcamera.m3u8":
                            if (ipCameraGroupHandler.hlsTurnedOn) {
                                String debugMe = ipCameraGroupHandler.getPlayList();
                                log.debug("playlist is:{}", debugMe);
                                sendString(ctx, debugMe, "application/x-mpegurl");
                                return;
                            } else {
                                log.warn("HLS requires the groups startStream channel to be turned on first. Just starting it now.");
                                ipCameraGroupHandler.start(OnOffType.ON);
                            }
                            break;
                        case "/ipcamera.jpg":
                            sendSnapshotImage(ctx, "image/jpg");
                            return;
                        default:
                            if (httpRequest.uri().contains(".ts")) {
                                sendFile(ctx, resolveIndexToPath(httpRequest.uri()) + httpRequest.uri().substring(2),
                                        "video/MP2T");
                            } else if (httpRequest.uri().contains(".jpg")) {
                                sendFile(ctx, httpRequest.uri(), "image/jpg");
                            } else if (httpRequest.uri().contains(".m4s") || httpRequest.uri().contains(".mp4")) {
                                sendFile(ctx, httpRequest.uri(), "video/mp4");
                            }
                    }
                }
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private void sendSnapshotImage(ChannelHandlerContext ctx, String contentType) {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        if (ipCameraGroupHandler.cameraIndex >= ipCameraGroupHandler.cameraOrder.size()) {
            log.debug("WARN: Openhab may still be starting, or all cameras in the group are OFFLINE.");
            return;
        }
        IpCameraHandler handler = ipCameraGroupHandler.cameraOrder.get(ipCameraGroupHandler.cameraIndex);
        handler.lockCurrentSnapshot.lock();
        try {
            ByteBuf snapshotData = Unpooled.copiedBuffer(handler.currentSnapshot);
            response.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            response.headers().add(HttpHeaderNames.CONTENT_LENGTH, snapshotData.readableBytes());
            response.headers().add("Access-Control-Allow-Origin", "*");
            response.headers().add("Access-Control-Expose-Headers", "*");
            ctx.channel().write(response);
            ctx.channel().write(snapshotData);
            ByteBuf footerBbuf = Unpooled.copiedBuffer("\r\n", 0, 2, StandardCharsets.UTF_8);
            ctx.channel().writeAndFlush(footerBbuf);
        } finally {
            handler.lockCurrentSnapshot.unlock();
        }
    }

    private void sendFile(ChannelHandlerContext ctx, String fileUri, String contentType) throws IOException {
        log.trace("file is :{}", fileUri);
        File file = new File(fileUri);
        ChunkedFile chunkedFile = new ChunkedFile(file);
        ByteBuf footerBbuf = Unpooled.copiedBuffer("\r\n", 0, 2, StandardCharsets.UTF_8);
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, chunkedFile.length());
        response.headers().add("Access-Control-Allow-Origin", "*");
        response.headers().add("Access-Control-Expose-Headers", "*");
        ctx.channel().write(response);
        ctx.channel().write(chunkedFile);
        ctx.channel().writeAndFlush(footerBbuf);
    }

    private void sendString(ChannelHandlerContext ctx, String contents, String contentType) {
        ByteBuf contentsBbuf = Unpooled.copiedBuffer(contents, 0, contents.length(), StandardCharsets.UTF_8);
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, contentsBbuf.readableBytes());
        response.headers().add("Access-Control-Allow-Origin", "*");
        response.headers().add("Access-Control-Expose-Headers", "*");
        ByteBuf footerBbuf = Unpooled.copiedBuffer("\r\n", 0, 2, StandardCharsets.UTF_8);
        ctx.channel().write(response);
        ctx.channel().write(contentsBbuf);
        ctx.channel().writeAndFlush(footerBbuf);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause == null || ctx == null) {
            return;
        }
        if (cause.toString().contains("Connection reset by peer")) {
            log.debug("Connection reset by peer.");
        } else if (cause.toString().contains("An established connection was aborted by the software")) {
            log.debug("An established connection was aborted by the software");
        } else if (cause.toString().contains("An existing connection was forcibly closed by the remote host")) {
            log.debug("An existing connection was forcibly closed by the remote host");
        } else if (cause.toString().contains("(No such file or directory)")) {
            log.info(
                    "IpCameras file server could not find the requested file. This may happen if ffmpeg is still creating the file.");
        } else {
            log.warn("Exception caught from stream server:{}", cause.getMessage());
        }
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == null || ctx == null) {
            return;
        }
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.WRITER_IDLE) {
                log.debug("Stream server is going to close an idle channel.");
                ctx.close();
            }
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (ctx == null) {
            return;
        }
        ctx.close();
    }
}
