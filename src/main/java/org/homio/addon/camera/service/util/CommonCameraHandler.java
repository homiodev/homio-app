package org.homio.addon.camera.service.util;

import static org.homio.addon.camera.CameraController.camerasOpenStreams;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.onvif.util.ChannelTracking;
import org.homio.addon.camera.onvif.util.Helper;
import org.homio.addon.camera.service.OnvifCameraService;

// These methods handle the response from all camera brands, nothing specific to 1 brand.
@Log4j2
@RequiredArgsConstructor
public class CommonCameraHandler extends ChannelDuplexHandler {

    private int bytesToReceive = 0;
    private int bytesAlreadyReceived = 0;
    private byte[] incomingJpeg = new byte[0];
    private String incomingMessage = "";
    private String contentType = "empty";
    private String boundary = "";
    private @Setter String requestUrl = "ffmpeg";

    private final OnvifCameraService service;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg == null || ctx == null) {
            return;
        }
        try {
            if (msg instanceof HttpResponse response) {
                if (response.status().code() == 200) {
                    if (!response.headers().isEmpty()) {
                        for (String name : response.headers().names()) {
                            // Some cameras use first letter uppercase and others don't.
                            switch (name.toLowerCase()) { // Possible localization issues doing this
                                case "content-type" -> contentType = response.headers().getAsString(name);
                                case "content-length" ->
                                        bytesToReceive = Integer.parseInt(response.headers().getAsString(name));
                            }
                        }
                        if (contentType.contains("multipart")) {
                            boundary = Helper.searchString(contentType, "boundary=");
                            if (service.urls.getMjpegUri().equals(requestUrl)) {
                                if (msg instanceof HttpMessage) {
                                    // very start of stream only
                                    service.setMjpegContentType(contentType);
                                    camerasOpenStreams.get(service.getEntityID()).openStreams.updateContentType(contentType, boundary);
                                }
                            }
                        } else if (contentType.contains("image/jp")) {
                            if (bytesToReceive == 0) {
                                bytesToReceive = 768000; // 0.768 Mbyte when no Content-Length is sent
                                log.debug("[{}]: Camera has no Content-Length header, we have to guess how much RAM.", service.getEntityID());
                            }
                            incomingJpeg = new byte[bytesToReceive];
                        }
                    }
                } else {
                    // Non 200 OK replies are logged and handled in pipeline by MyNettyAuthHandler.java
                    return;
                }
            }
            if (msg instanceof HttpContent content) {
                if (service.urls.getMjpegUri().equals(requestUrl) && !(content instanceof LastHttpContent)) {
                    // multiple MJPEG stream packets come back as this.
                    byte[] chunkedFrame = new byte[content.content().readableBytes()];
                    content.content().getBytes(content.content().readerIndex(), chunkedFrame);
                    camerasOpenStreams.get(service.getEntityID()).openStreams.queueFrame(chunkedFrame);
                } else {

                    // Found some cameras use Content-Type: image/jpg instead of image/jpeg
                    if (contentType.contains("image/jp")) {
                        for (int i = 0; i < content.content().capacity(); i++) {
                            incomingJpeg[bytesAlreadyReceived++] = content.content().getByte(i);
                        }
                        if (content instanceof LastHttpContent) {
                            service.processSnapshot(incomingJpeg);
                            ctx.close();
                        }
                    } else { // incomingMessage that is not an IMAGE
                        if (incomingMessage.isEmpty()) {
                            incomingMessage = content.content().toString(CharsetUtil.UTF_8);
                        } else {
                            incomingMessage += content.content().toString(CharsetUtil.UTF_8);
                        }
                        bytesAlreadyReceived = incomingMessage.length();
                        if (content instanceof LastHttpContent) {
                            // If it is not an image send it on to the next handler//
                            if (bytesAlreadyReceived != 0) {
                                super.channelRead(ctx, incomingMessage);
                            }
                        }
                        // Alarm Streams never have a LastHttpContent as they always stay open//
                        else if (contentType.contains("multipart")) {
                            int beginIndex, endIndex;
                            if (bytesToReceive == 0) {
                                beginIndex = incomingMessage.indexOf("Content-Length:");
                                if (beginIndex != -1) {
                                    endIndex = incomingMessage.indexOf("\r\n", beginIndex);
                                    if (endIndex != -1) {
                                        bytesToReceive = Integer.parseInt(
                                                incomingMessage.substring(beginIndex + 15, endIndex).strip());
                                    }
                                }
                            }
                            // --boundary and headers are not included in the Content-Length value
                            if (bytesAlreadyReceived > bytesToReceive) {
                                // Check if message has a second --boundary
                                endIndex = incomingMessage.indexOf("--" + boundary, bytesToReceive);
                                Object reply;
                                if (endIndex == -1) {
                                    reply = incomingMessage;
                                    incomingMessage = "";
                                    bytesToReceive = 0;
                                    bytesAlreadyReceived = 0;
                                } else {
                                    reply = incomingMessage.substring(0, endIndex);
                                    incomingMessage = incomingMessage.substring(endIndex);
                                    bytesToReceive = 0;// Triggers search next time for Content-Length:
                                    bytesAlreadyReceived = incomingMessage.length() - endIndex;
                                }
                                super.channelRead(ctx, reply);
                            }
                        }
                    }
                }
            } else { // msg is not HttpContent
                // Foscam cameras need this
                if (!contentType.contains("image/jp") && bytesAlreadyReceived != 0) {
                    log.trace("[{}]: Packet back from camera is {}", service.getEntityID(), incomingMessage);
                    super.channelRead(ctx, incomingMessage);
                }
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause == null || ctx == null) {
            return;
        }
        if (cause instanceof ArrayIndexOutOfBoundsException) {
            log.debug("[{}]: Camera sent {} bytes when the content-length header was {}.", service.getEntityID(),
                    bytesAlreadyReceived, bytesToReceive);
        } else {
            log.warn("[{}]: !!!! Camera possibly closed the channel on the binding, cause reported is: {}",
                    service.getEntityID(), cause.getMessage());
        }
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (ctx == null) {
            return;
        }
        if (evt instanceof IdleStateEvent e) {
            // If camera does not use the channel for X amount of time it will close.
            if (e.state() == IdleState.READER_IDLE) {
                String urlToKeepOpen = service.getBrandHandler().getUrlToKeepOpenForIdleStateEvent();
                ChannelTracking channelTracking = service.getChannelTrack(urlToKeepOpen);
                if (channelTracking != null) {
                    if (Objects.equals(channelTracking.getChannel(), ctx.channel())) {
                        return; // don't auto close this as it is for the alarms.
                    }
                }
                log.debug("[{}]: Closing an idle channel for camera:{}", service.getEntityID(), service.getEntity().getTitle());
                ctx.close();
            }
        }
    }
}
