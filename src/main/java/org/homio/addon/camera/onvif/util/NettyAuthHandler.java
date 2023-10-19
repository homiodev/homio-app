package org.homio.addon.camera.onvif.util;

import static org.homio.addon.camera.service.util.CameraUtils.calcMD5Hash;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.CharsetUtil;
import java.security.SecureRandom;
import java.util.Random;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.entity.IpCameraEntity;
import org.homio.addon.camera.service.IpCameraService;
import org.homio.api.model.Status;

/**
 * responsible for handling the basic and digest auths
 */
@Log4j2
@Sharable // Maybe need create 'prototype' instance because 'httpMethod and httpUrl' is shareable
@RequiredArgsConstructor
public class NettyAuthHandler extends ChannelDuplexHandler {
    public static final String AUTH_HANDLER = "authorizationHandler";

    private final IpCameraService service;

    private String httpMethod = "";
    private String httpUrl = "";
    private byte ncCounter = 0;

    private @Getter String basicAuth = "";
    private @Getter boolean useDigestAuth = false;

    public void setURL(String method, String url) {
        httpUrl = url;
        httpMethod = method;
    }

    // Method can be used a few ways. processAuth(null, string,string, false) to return the digest on demand, and
    // processAuth(challString, string,string, true) to auto send new packet
    // First run it should not have authenticate as null
    // nonce is reused if authenticate is null so the NC needs to increment to allow this//
    public void processAuth(String authenticate, String httpMethod, String requestURI, boolean reSend) {
        if (authenticate.contains("Basic realm=\"")) {
            if (useDigestAuth) {
                // Possible downgrade authenticate attack avoided.
                return;
            }
            log.debug("Setting up the camera to use Basic Auth and resending last request with correct auth.");
            if (setBasicAuth(true)) {
                service.sendHttpRequest(httpMethod, requestURI, null);
            }
            return;
        }

        /////// Fresh Digest Authenticate method follows as Basic is already handled and returned ////////
        String realm = Helper.searchString(authenticate, "realm=\"");
        if (realm.isEmpty()) {
            log.warn("Could not find a valid WWW-Authenticate response in :{}", authenticate);
            return;
        }
        String nonce = Helper.searchString(authenticate, "nonce=\"");
        String opaque = Helper.searchString(authenticate, "opaque=\"");
        String qop = Helper.searchString(authenticate, "qop=\"");

        if (!qop.isEmpty()) {
            useDigestAuth = true;
        } else {
            log.warn(
                    "!!!! Something is wrong with the reply back from the camera. WWW-Authenticate header: qop:{}, realm:{}",
                qop, realm);
        }

        String stale = Helper.searchString(authenticate, "stale=\"");
        if (stale.equalsIgnoreCase("true")) {
            log.debug("Camera reported stale=true which normally means the NONCE has expired.");
        }

        String username = service.getEntity().getUser();
        String password = service.getEntity().getPassword().asString();

        if (password.isEmpty()) {
            service.disposeAndSetStatus(Status.ERROR, "Camera gave a 401 reply: You need to provide a password.");
            return;
        }
        // create the MD5 hashes
        String ha1 = username + ":" + realm + ":" + password;
        ha1 = calcMD5Hash(ha1);
        Random random = new SecureRandom();
        String cnonce = Integer.toHexString(random.nextInt());
        ncCounter = (ncCounter > 125) ? 1 : ++ncCounter;
        String nc = String.format("%08X", ncCounter); // 8 digit hex number
        String ha2 = httpMethod + ":" + requestURI;
        ha2 = calcMD5Hash(ha2);

        String response = ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2;
        response = calcMD5Hash(response);

        String digestString = "username=\"" + username + "\", realm=\"" + realm + "\", nonce=\"" + nonce + "\", uri=\""
                + requestURI + "\", cnonce=\"" + cnonce + "\", nc=" + nc + ", qop=\"" + qop + "\", response=\""
                + response + "\"";
        if (!opaque.isEmpty()) {
            digestString += ", opaque=\"" + opaque + "\"";
        }

        if (reSend) {
            service.sendHttpRequest(httpMethod, requestURI, digestString);
        }
    }

    // false clears the stored user/pass hash, true creates the hash
    public boolean setBasicAuth(boolean useBasic) {
        if (!useBasic) {
            log.debug("[{}]: Clearing out the stored BASIC auth now.", service.getEntityID());
            basicAuth = "";
            return false;
        } else if (!basicAuth.isEmpty()) {
            // due to camera may have been sent multiple requests before the auth was set, this may trigger falsely.
            log.warn("[{}]: Camera is reporting your username and/or password is wrong.", service.getEntityID());
            return false;
        }
        IpCameraEntity entity = service.getEntity();
        if (!entity.getUser().isEmpty() && !entity.getPassword().isEmpty()) {
            String authString = entity.getUser() + ":" + entity.getPassword().asString();
            ByteBuf byteBuf = null;
            try {
                byteBuf = Base64.encode(Unpooled.wrappedBuffer(authString.getBytes(CharsetUtil.UTF_8)));
                basicAuth = byteBuf.getCharSequence(0, byteBuf.capacity(), CharsetUtil.UTF_8).toString();
            } finally {
                if (byteBuf != null) {
                    byteBuf.release();
                }
            }
            return true;
        } else {
            service.disposeAndSetStatus(Status.ERROR, "Camera is asking for Basic Auth when you have not provided a username and/or password.");
        }
        return false;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg == null || ctx == null) {
            return;
        }
        boolean closeConnection = true;
        String authenticate = "";
        if (msg instanceof HttpResponse response) {
            if (response.status().code() == 401) {
                if (!response.headers().isEmpty()) {
                    for (CharSequence name : response.headers().names()) {
                        for (CharSequence value : response.headers().getAll(name)) {
                            if (name.toString().equalsIgnoreCase("WWW-Authenticate")) {
                                authenticate = value.toString();
                            }
                            if (name.toString().equalsIgnoreCase("Connection")
                                    && value.toString().contains("keep-alive")) {
                                // closeConnection = false;
                                // trial this for a while to see if it solves too many bytes with digest turned on.
                                closeConnection = true;
                            }
                        }
                    }
                    if (!authenticate.isEmpty()) {
                        processAuth(authenticate, httpMethod, httpUrl, true);
                    } else {
                        service.disposeAndSetStatus(Status.ERROR,
                                "Camera gave no WWW-Authenticate: Your login details must be wrong.");
                    }
                    if (closeConnection) {
                        ctx.close();// needs to be here
                    }
                }
            } else if (response.status().code() != 200) {
                log.debug("Camera at IP:{} gave a reply with a response code of :{}",
                        service.getEntity().getIp(), response.status().code());
            }
        }
        // Pass the Message back to the pipeline for the next handler to process//
        super.channelRead(ctx, msg);
    }

    public void authenticateRequest(FullHttpRequest request, String digestString) {
        if (!basicAuth.isEmpty()) {
            if (useDigestAuth) {
                log.warn("[{}]: Camera at IP:{} had both Basic and Digest set to be used", service.getEntityID(), service.getEntity().getIp());
                setBasicAuth(false);
            } else {
                request.headers().set(HttpHeaderNames.AUTHORIZATION, "Basic " + basicAuth);
            }
        }

        if (useDigestAuth) {
            if (digestString != null) {
                request.headers().set(HttpHeaderNames.AUTHORIZATION, "Digest " + digestString);
            }
        }
    }

    public void dispose() {
        basicAuth = ""; // clear out stored Password hash
        useDigestAuth = false;
    }
}
