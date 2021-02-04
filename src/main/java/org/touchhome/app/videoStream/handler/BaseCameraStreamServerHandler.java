package org.touchhome.app.videoStream.handler;

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
import org.touchhome.app.videoStream.ffmpeg.Ffmpeg;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * responsible for handling streams
 */
@Log4j2
public abstract class BaseCameraStreamServerHandler<T extends BaseFfmpegCameraHandler> extends ChannelInboundHandlerAdapter {

    protected final T cameraHandler;

    private final String whiteList;
    private byte[] incomingJpeg = new byte[0];
    private int receivedBytes = 0;
    private boolean updateSnapshot = false;

    public BaseCameraStreamServerHandler(T cameraHandler) {
        this.cameraHandler = cameraHandler;
        this.whiteList = "(127.0.0.1)(" + TouchHomeUtils.MACHINE_IP_ADDRESS + ")";
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
                if (handleHttpRequest(ctx, (HttpRequest) msg)) return;
            }
            if (msg instanceof HttpContent) {
                handleHttpContent((HttpContent) msg);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

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
            if (updateSnapshot) {
                cameraHandler.processSnapshot(incomingJpeg);
            } else {
                handleLastHttpContent(incomingJpeg);
            }
            receivedBytes = 0;
        }
    }

    protected abstract void handleLastHttpContent(byte[] incomingJpeg);

    private boolean handleHttpRequest(ChannelHandlerContext ctx, HttpRequest httpRequest) throws IOException, InterruptedException {
        String requestIP = "(" + ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress() + ")";
        if (!whiteList.contains(requestIP)) {
            log.warn("The request made from {} was not in the whitelist and will be ignored.", requestIP);
            return true;
        }
        if ("GET".equalsIgnoreCase(httpRequest.method().toString())) {
            log.debug("Stream Server received request \tGET:{}", httpRequest.uri());
            // Some browsers send a query string after the path when refreshing a picture.
            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(httpRequest.uri());
            if (handleHttpRequest(queryStringDecoder, ctx)) {
                return true;
            }
            switch (queryStringDecoder.path()) {
                case "/ipcamera.m3u8":
                    Ffmpeg localFfmpeg = cameraHandler.ffmpegHLS;
                    if (localFfmpeg == null) {
                        cameraHandler.startHLSStream();
                    } else if (!localFfmpeg.getIsAlive()) {
                        localFfmpeg.startConverting();
                    } else {
                        localFfmpeg.setKeepAlive(8);
                        sendFile(ctx, httpRequest.uri(), "application/x-mpegurl");
                        return true;
                    }
                    // Allow files to be created, or you get old m3u8 from the last time this ran.
                    TimeUnit.MILLISECONDS.sleep(4500);
                    sendFile(ctx, httpRequest.uri(), "application/x-mpegurl");
                    return true;
                case "/ipcamera.mpd":
                    sendFile(ctx, httpRequest.uri(), "application/dash+xml");
                    return true;
                case "/ipcamera.gif":
                    sendFile(ctx, httpRequest.uri(), "image/gif");
                    return true;
                case "/ipcamera.jpg":
                    sendSnapshotImage(ctx, "image/jpg");
                    return true;
                case "/snapshots.mjpeg":
                    return true;
                case "/ipcamera0.ts":
                default:
                    if (httpRequest.uri().contains(".ts")) {
                        sendFile(ctx, queryStringDecoder.path(), "video/MP2T");
                    } else if (httpRequest.uri().contains(".gif")) {
                        sendFile(ctx, queryStringDecoder.path(), "image/gif");
                    } else if (httpRequest.uri().contains(".jpg")) {
                        // Allow access to the preroll and postroll jpg files
                        sendFile(ctx, queryStringDecoder.path(), "image/jpg");
                    } else if (httpRequest.uri().contains(".m4s") || httpRequest.uri().contains(".mp4")) {
                        sendFile(ctx, queryStringDecoder.path(), "video/mp4");
                    }
                    return true;
            }
        } else if ("POST".equalsIgnoreCase(httpRequest.method().toString())) {
            if (!streamServerReceivedPostHandler(httpRequest)) {
                switch (httpRequest.uri()) {
                    case "/ipcamera.jpg":
                        break;
                    case "/snapshot.jpg":
                        updateSnapshot = true;
                        break;
                    default:
                        log.debug("Stream Server received unknown request \tPOST:{}", httpRequest.uri());
                        break;
                }
            }
        }
        return false;
    }

    protected abstract boolean handleHttpRequest(QueryStringDecoder queryStringDecoder, ChannelHandlerContext ctx);

    protected abstract boolean streamServerReceivedPostHandler(HttpRequest httpRequest);

    private void sendSnapshotImage(ChannelHandlerContext ctx, String contentType) {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        cameraHandler.lockCurrentSnapshot.lock();
        try {
            ByteBuf snapshotData = Unpooled.copiedBuffer(cameraHandler.currentSnapshot);
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
            cameraHandler.lockCurrentSnapshot.unlock();
        }
    }

    private void sendFile(ChannelHandlerContext ctx, String fileUri, String contentType) throws IOException {
        Path file = cameraHandler.getFfmpegOutputPath().resolve(fileUri.substring(1));

        ChunkedFile chunkedFile = new ChunkedFile(file.toFile());
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, chunkedFile.length());
        response.headers().add("Access-Control-Allow-Origin", "*");
        response.headers().add("Access-Control-Expose-Headers", "*");
        ctx.channel().write(response);
        ctx.channel().write(chunkedFile);
        ByteBuf footerBbuf = Unpooled.copiedBuffer("\r\n", 0, 2, StandardCharsets.UTF_8);
        ctx.channel().writeAndFlush(footerBbuf);
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
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (ctx == null) {
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
        this.handlerChildRemoved(ctx);
    }

    protected abstract void handlerChildRemoved(ChannelHandlerContext ctx);
}
