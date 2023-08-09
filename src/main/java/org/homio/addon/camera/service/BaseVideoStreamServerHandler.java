package org.homio.addon.camera.service;

import static org.homio.addon.camera.VideoConstants.CHANNEL_START_STREAM;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.homio.api.state.OnOffType;
import org.homio.api.util.CommonUtils;

/**
 * responsible for handling streams
 */
@SuppressWarnings("unused")
@Log4j2
public abstract class BaseVideoStreamServerHandler<S extends BaseVideoService> extends ChannelInboundHandlerAdapter {

    protected final S videoService;

    private final String whiteList;
    private final String entityID;
    private byte[] incomingJpeg = new byte[0];
    private int receivedBytes = 0;
    private boolean updateSnapshot = false;

    public BaseVideoStreamServerHandler(S videoService) {
        this.videoService = videoService;
        this.entityID = videoService.getEntityID();
        this.whiteList = "(127.0.0.1)(" + CommonUtils.MACHINE_IP_ADDRESS + ")";
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (ctx == null) {
            return;
        }
        try {
            if (msg instanceof HttpRequest) {
                if (handleHttpRequest(ctx, (HttpRequest) msg)) {return;}
            }
            if (msg instanceof HttpContent) {
                handleHttpContent((HttpContent) msg);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (ctx == null || cause == null) {
            return;
        }
        if (cause.toString().contains("Connection reset by peer")) {
            log.trace("Connection reset by peer.");
        } else if (cause.toString().contains("An established connection was aborted by the software")) {
            log.debug("[{}]: An established connection was aborted by the software", entityID);
        } else if (cause.toString().contains("An existing connection was forcibly closed by the remote host")) {
            log.debug("[{}]: An existing connection was forcibly closed by the remote host", entityID);
        } else if (cause.toString().contains("(No such file or directory)")) {
            log.info("[{}]: IpVideo file server could not find the requested file. This may happen if ffmpeg is still creating the " +
                "file.", entityID);
        } else {
            log.warn("[{}]: Exception caught from stream server:{}", entityID, cause.getMessage());
        }
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (ctx == null) {
            return;
        }
        if (evt instanceof IdleStateEvent e) {
            if (e.state() == IdleState.WRITER_IDLE) {
                log.debug("[{}]: Stream server is going to close an idle channel.", entityID);
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
        this.handlerChildRemoved(ctx);
    }

    protected abstract void handleLastHttpContent(byte[] incomingJpeg);

    protected void handleChildrenHttpRequest(QueryStringDecoder queryStringDecoder, ChannelHandlerContext ctx) {

    }

    protected abstract boolean streamServerReceivedPostHandler(HttpRequest httpRequest);

    protected abstract void handlerChildRemoved(ChannelHandlerContext ctx);

    private void handleHttpContent(HttpContent msg) {
        if (receivedBytes == 0) {
            incomingJpeg = new byte[msg.content().readableBytes()];
            msg.content().getBytes(0, incomingJpeg, 0, msg.content().readableBytes());
        } else {
            byte[] temp = incomingJpeg;
            incomingJpeg = new byte[receivedBytes + msg.content().readableBytes()];
            System.arraycopy(temp, 0, incomingJpeg, 0, temp.length);
            msg.content().getBytes(0, incomingJpeg, temp.length, msg.content().readableBytes());
        }
        receivedBytes = incomingJpeg.length;

        if (msg instanceof LastHttpContent) {
            videoService.bringVideoOnline();
            if (updateSnapshot) {
                videoService.processSnapshot(incomingJpeg);
            } else {
                handleLastHttpContent(incomingJpeg);
            }
            receivedBytes = 0;
        }
    }

    private boolean handleHttpRequest(ChannelHandlerContext ctx, HttpRequest httpRequest)
        throws IOException, InterruptedException {
        String requestIP = "(" + ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress() + ")";

        if (!whiteList.contains(requestIP)) {
            log.warn("[{}]: The request made from {} was not in the whitelist and will be ignored.",
                entityID, requestIP);
            return true;
        }
        if ("GET".equalsIgnoreCase(httpRequest.method().toString())) {
            log.debug("[{}]: Stream Server received request \tGET:{}", entityID, httpRequest.uri());
            // Some browsers send a query string after the path when refreshing a picture.
            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(httpRequest.uri());

            handleChildrenHttpRequest(queryStringDecoder, ctx);

            switch (queryStringDecoder.path()) {
                case "/ipvideo.m3u8" -> {
                    if (!videoService.ffmpegHLS.getIsAlive()) {
                        if (videoService.ffmpegHLS.startConverting()) {
                            videoService.setAttribute(CHANNEL_START_STREAM, OnOffType.ON);
                        }
                    } else {
                        videoService.ffmpegHLS.setKeepAlive(8);
                        sendFile(ctx, httpRequest.uri(), "application/x-mpegurl", videoService.getFfmpegHLSOutputPath());
                        return true;
                    }
                    // Allow files to be created, or you get old m3u8 from the last time this ran.
                    TimeUnit.SECONDS.sleep(10);
                    sendFile(ctx, httpRequest.uri(), "application/x-mpegurl", videoService.getFfmpegHLSOutputPath());
                    return true;
                }
                case "/ipvideo.mpd" -> {
                    sendFile(ctx, httpRequest.uri(), "application/dash+xml", videoService.getFfmpegMP4OutputPath());
                    return true;
                }
                case "/ipvideo.gif" -> {
                    byte[] bytes = videoService.recordGifSync(null, 5);
                    sendNettyResponse(ctx, "image/gif", bytes);
                    return true;
                }
                case "/ipvideo.jpg" -> {
                    sendSnapshotImage(ctx);
                    return true;
                }
                case "/snapshots.mjpeg" -> {
                    return true;
                }
                default -> {
                    if (httpRequest.uri().contains(".ts")) {
                        sendFile(ctx, queryStringDecoder.path(), "video/MP2T", videoService.getFfmpegHLSOutputPath());
                    } else if (httpRequest.uri().contains(".gif")) {
                        sendFile(ctx, queryStringDecoder.path(), "image/gif", videoService.getFfmpegGifOutputPath());
                    } else if (httpRequest.uri().contains(".jpg")) {
                        // Allow access to the preroll and postroll jpg files
                        sendFile(ctx, queryStringDecoder.path(), "image/jpg", videoService.getFfmpegImageOutputPath());
                    } else if (httpRequest.uri().contains(".m4s") || httpRequest.uri().contains(".mp4")) {
                        sendFile(ctx, queryStringDecoder.path(), "video/mp4", videoService.getFfmpegMP4OutputPath());
                    }
                    return true;
                }
            }
        } else if ("POST".equalsIgnoreCase(httpRequest.method().toString())) {
            if (!streamServerReceivedPostHandler(httpRequest)) {
                switch (httpRequest.uri()) {
                    case "/ipvideo.jpg":
                        break;
                    case "/snapshot.jpg":
                        updateSnapshot = true;
                        break;
                    default:
                        log.debug("[{}]: Stream Server received unknown request \tPOST:{}",
                            entityID, httpRequest.uri());
                        break;
                }
            }
        }
        return false;
    }

    private void sendSnapshotImage(ChannelHandlerContext ctx) {
        videoService.lockCurrentSnapshot.lock();
        try {
            sendNettyResponse(ctx, "image/jpg", videoService.getLatestSnapshot());
        } finally {
            videoService.lockCurrentSnapshot.unlock();
        }
    }

    private void sendFile(ChannelHandlerContext ctx, String fileUri, String contentType, Path path) throws IOException {
        ChunkedFile chunkedFile = new ChunkedFile(path.resolve(fileUri.substring(1)).toFile());
        sendNettyResponse(ctx, contentType, chunkedFile.length(), chunkedFile);
    }

    private void sendNettyResponse(ChannelHandlerContext ctx, String contentType, byte[] data) {
        ByteBuf snapshotData = Unpooled.copiedBuffer(videoService.getLatestSnapshot());
        sendNettyResponse(ctx, contentType, snapshotData.readableBytes(), snapshotData);
    }

    private void sendNettyResponse(ChannelHandlerContext ctx, String contentType, long length, Object data) {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, length);
        response.headers().add("Access-Control-Allow-Origin", "*");
        response.headers().add("Access-Control-Expose-Headers", "*");
        ctx.channel().write(response);
        ctx.channel().write(data);
        ByteBuf footerBbuf = Unpooled.copiedBuffer("\r\n", 0, 2, StandardCharsets.UTF_8);
        ctx.channel().writeAndFlush(footerBbuf);
    }
}
