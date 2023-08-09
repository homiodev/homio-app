package org.homio.addon.camera.onvif.util;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import lombok.extern.log4j.Log4j2;
import org.homio.api.model.Status;
import org.homio.addon.camera.service.OnvifCameraService;

/**
 * responsible for handling the basic and digest auths
 */
@Log4j2
public class MyNettyAuthHandler extends ChannelDuplexHandler {

  private final String username;
  private final String password;
  private final OnvifCameraService service;

  private String httpMethod = "", httpUrl = "";
  private byte ncCounter = 0;
  private String nonce = "", opaque = "", qop = "";
  private String realm = "";

  public MyNettyAuthHandler(String user, String pass, OnvifCameraService service) {
    this.service = service;
    username = user;
    password = pass;
  }

  public void setURL(String method, String url) {
    httpUrl = url;
    httpMethod = method;
  }

  private String calcMD5Hash(String toHash) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("MD5");
      byte[] array = messageDigest.digest(toHash.getBytes());
      StringBuffer stringBuffer = new StringBuffer();
      for (int i = 0; i < array.length; ++i) {
        stringBuffer.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
      }
      return stringBuffer.toString();
    } catch (NoSuchAlgorithmException e) {
      log.warn("NoSuchAlgorithmException error when calculating MD5 hash");
    }
    return "";
  }

  // Method can be used a few ways. processAuth(null, string,string, false) to return the digest on demand, and
  // processAuth(challString, string,string, true) to auto send new packet
  // First run it should not have authenticate as null
  // nonce is reused if authenticate is null so the NC needs to increment to allow this//
  public void processAuth(String authenticate, String httpMethod, String requestURI, boolean reSend) {
    if (authenticate.contains("Basic realm=\"")) {
      if (service.useDigestAuth) {
        // Possible downgrade authenticate attack avoided.
        return;
      }
      log.debug("Setting up the camera to use Basic Auth and resending last request with correct auth.");
      if (service.setBasicAuth(true)) {
        service.sendHttpRequest(httpMethod, requestURI, null);
      }
      return;
    }

    /////// Fresh Digest Authenticate method follows as Basic is already handled and returned ////////
    realm = Helper.searchString(authenticate, "realm=\"");
    if (realm.isEmpty()) {
      log.warn("Could not find a valid WWW-Authenticate response in :{}", authenticate);
      return;
    }
    nonce = Helper.searchString(authenticate, "nonce=\"");
    opaque = Helper.searchString(authenticate, "opaque=\"");
    qop = Helper.searchString(authenticate, "qop=\"");

    if (!qop.isEmpty() && !realm.isEmpty()) {
      service.useDigestAuth = true;
    } else {
      log.warn(
          "!!!! Something is wrong with the reply back from the camera. WWW-Authenticate header: qop:{}, realm:{}",
          qop, realm);
    }

    String stale = Helper.searchString(authenticate, "stale=\"");
    if (stale.equalsIgnoreCase("true")) {
      log.debug("Camera reported stale=true which normally means the NONCE has expired.");
    }

    if (password.isEmpty()) {
      service.disposeAndSetStatus(Status.ERROR, "Camera gave a 401 reply: You need to provide a password.");
      return;
    }
    // create the MD5 hashes
    String ha1 = username + ":" + realm + ":" + password;
    ha1 = calcMD5Hash(ha1);
    Random random = new Random();
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

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg == null || ctx == null) {
      return;
    }
    boolean closeConnection = true;
    String authenticate = "";
    if (msg instanceof HttpResponse) {
      HttpResponse response = (HttpResponse) msg;
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

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) {
  }
}
