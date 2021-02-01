package org.touchhome.app.camera.openhub.onvif;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.util.Pair;
import org.touchhome.app.camera.openhub.Helper;
import org.touchhome.app.camera.openhub.handler.IpCameraHandler;
import org.touchhome.bundle.api.measure.OnOffType;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.touchhome.app.camera.openhub.IpCameraBindingConstants.*;

/**
 * This is a basic Netty implementation for connecting and communicating to ONVIF cameras.
 */
@Log4j2
public class OnvifConnection {
    public enum RequestType {
        AbsoluteMove,
        AddPTZConfiguration,
        ContinuousMoveLeft,
        ContinuousMoveRight,
        ContinuousMoveUp,
        ContinuousMoveDown,
        Stop,
        ContinuousMoveIn,
        ContinuousMoveOut,
        CreatePullPointSubscription,
        GetCapabilities,
        GetDeviceInformation,
        GetProfiles,
        GetServiceCapabilities,
        GetSnapshotUri,
        GetStreamUri,
        GetSystemDateAndTime,
        Subscribe,
        Unsubscribe,
        PullMessages,
        GetEventProperties,
        RelativeMoveLeft,
        RelativeMoveRight,
        RelativeMoveUp,
        RelativeMoveDown,
        RelativeMoveIn,
        RelativeMoveOut,
        Renew,
        GetConfigurations,
        GetConfigurationOptions,
        GetConfiguration,
        SetConfiguration,
        GetNodes,
        GetStatus,
        GotoPreset,
        GetPresets
    }

    private Bootstrap bootstrap;
    private EventLoopGroup mainEventLoopGroup = new NioEventLoopGroup();
    private String ipAddress = "";
    private String user = "";
    private String password = "";
    private int onvifPort = 80;
    private String deviceXAddr = "/onvif/device_service";
    private String eventXAddr = "/onvif/device_service";
    private String mediaXAddr = "/onvif/device_service";
    @SuppressWarnings("unused")
    private String imagingXAddr = "/onvif/device_service";
    private String ptzXAddr = "/onvif/ptz_service";
    private String subscriptionXAddr = "/onvif/device_service";
    private boolean isConnected = false;
    private int mediaProfileIndex = 0;
    private String snapshotUri = "";
    private String rtspUri = "";
    private IpCameraHandler ipCameraHandler;
    private boolean usingEvents = false;

    // These hold the cameras PTZ position in the range that the camera uses, ie
    // mine is -1 to +1
    private Float panRangeMin = -1.0f;
    private Float panRangeMax = 1.0f;
    private Float tiltRangeMin = -1.0f;
    private Float tiltRangeMax = 1.0f;
    private Float zoomMin = 0.0f;
    private Float zoomMax = 1.0f;
    // These hold the PTZ values for updating Openhabs controls in 0-100 range
    private Float currentPanPercentage = 0.0f;
    private Float currentTiltPercentage = 0.0f;
    private Float currentZoomPercentage = 0.0f;
    private Float currentPanCamValue = 0.0f;
    private Float currentTiltCamValue = 0.0f;
    private Float currentZoomCamValue = 0.0f;
    private String ptzNodeToken = "000";
    private String ptzConfigToken = "000";
    private int presetTokenIndex = 0;
    private List<String> presetTokens = new LinkedList<>();
    private List<String> presetNames = new LinkedList<>();
    private List<String> mediaProfileTokens = new LinkedList<>();
    private boolean ptzDevice = true;

    public OnvifConnection(IpCameraHandler ipCameraHandler, String ipAddress, String user, String password) {
        this.ipCameraHandler = ipCameraHandler;
        if (!ipAddress.isEmpty()) {
            this.user = user;
            this.password = password;
            getIPandPortFromUrl(ipAddress);
        }
    }

    String getXml(RequestType requestType) {
        switch (requestType) {
            case AbsoluteMove:
                return "<AbsoluteMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex) + "</ProfileToken><Position><PanTilt x=\""
                        + currentPanCamValue + "\" y=\"" + currentTiltCamValue
                        + "\" space=\"http://www.onvif.org/ver10/tptz/PanTiltSpaces/PositionGenericSpace\">\n"
                        + "</PanTilt>\n" + "<Zoom x=\"" + currentZoomCamValue
                        + "\" space=\"http://www.onvif.org/ver10/tptz/ZoomSpaces/PositionGenericSpace\">\n"
                        + "</Zoom>\n" + "</Position>\n"
                        + "<Speed><PanTilt x=\"0.1\" y=\"0.1\" space=\"http://www.onvif.org/ver10/tptz/PanTiltSpaces/GenericSpeedSpace\"></PanTilt><Zoom x=\"1.0\" space=\"http://www.onvif.org/ver10/tptz/ZoomSpaces/ZoomGenericSpeedSpace\"></Zoom>\n"
                        + "</Speed></AbsoluteMove>";
            case AddPTZConfiguration: // not tested to work yet
                return "<AddPTZConfiguration xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex) + "</ProfileToken><ConfigurationToken>"
                        + ptzConfigToken + "</ConfigurationToken></AddPTZConfiguration>";
            case ContinuousMoveLeft:
                return "<ContinuousMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex)
                        + "</ProfileToken><Velocity><PanTilt x=\"-0.5\" y=\"0\" xmlns=\"http://www.onvif.org/ver10/schema\"/></Velocity></ContinuousMove>";
            case ContinuousMoveRight:
                return "<ContinuousMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex)
                        + "</ProfileToken><Velocity><PanTilt x=\"0.5\" y=\"0\" xmlns=\"http://www.onvif.org/ver10/schema\"/></Velocity></ContinuousMove>";
            case ContinuousMoveUp:
                return "<ContinuousMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex)
                        + "</ProfileToken><Velocity><PanTilt x=\"0\" y=\"-0.5\" xmlns=\"http://www.onvif.org/ver10/schema\"/></Velocity></ContinuousMove>";
            case ContinuousMoveDown:
                return "<ContinuousMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex)
                        + "</ProfileToken><Velocity><PanTilt x=\"0\" y=\"0.5\" xmlns=\"http://www.onvif.org/ver10/schema\"/></Velocity></ContinuousMove>";
            case Stop:
                return "<Stop xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex)
                        + "</ProfileToken><PanTilt>true</PanTilt><Zoom>true</Zoom></Stop>";
            case ContinuousMoveIn:
                return "<ContinuousMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex)
                        + "</ProfileToken><Velocity><Zoom x=\"0.5\" xmlns=\"http://www.onvif.org/ver10/schema\"/></Velocity></ContinuousMove>";
            case ContinuousMoveOut:
                return "<ContinuousMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex)
                        + "</ProfileToken><Velocity><Zoom x=\"-0.5\" xmlns=\"http://www.onvif.org/ver10/schema\"/></Velocity></ContinuousMove>";
            case CreatePullPointSubscription:
                return "<CreatePullPointSubscription xmlns=\"http://www.onvif.org/ver10/events/wsdl\"><InitialTerminationTime>PT600S</InitialTerminationTime></CreatePullPointSubscription>";
            case GetCapabilities:
                return "<GetCapabilities xmlns=\"http://www.onvif.org/ver10/device/wsdl\"><Category>All</Category></GetCapabilities>";

            case GetDeviceInformation:
                return "<GetDeviceInformation xmlns=\"http://www.onvif.org/ver10/device/wsdl\"/>";
            case GetProfiles:
                return "<GetProfiles xmlns=\"http://www.onvif.org/ver10/media/wsdl\"/>";
            case GetServiceCapabilities:
                return "<GetServiceCapabilities xmlns=\"http://docs.oasis-open.org/wsn/b-2/\"></GetServiceCapabilities>";
            case GetSnapshotUri:
                return "<GetSnapshotUri xmlns=\"http://www.onvif.org/ver10/media/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex) + "</ProfileToken></GetSnapshotUri>";
            case GetStreamUri:
                return "<GetStreamUri xmlns=\"http://www.onvif.org/ver10/media/wsdl\"><StreamSetup><Stream xmlns=\"http://www.onvif.org/ver10/schema\">RTP-Unicast</Stream><Transport xmlns=\"http://www.onvif.org/ver10/schema\"><Protocol>RTSP</Protocol></Transport></StreamSetup><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex) + "</ProfileToken></GetStreamUri>";
            case GetSystemDateAndTime:
                return "<GetSystemDateAndTime xmlns=\"http://www.onvif.org/ver10/device/wsdl\"/>";
            case Subscribe:
                return "<Subscribe xmlns=\"http://docs.oasis-open.org/wsn/b-2/\"><ConsumerReference><Address>http://"
                        + ipCameraHandler.hostIp + ":" + ipCameraHandler.getCameraEntity().getServerPort()
                        + "/OnvifEvent</Address></ConsumerReference></Subscribe>";
            case Unsubscribe:
                return "<Unsubscribe xmlns=\"http://docs.oasis-open.org/wsn/b-2/\"></Unsubscribe>";
            case PullMessages:
                return "<PullMessages xmlns=\"http://www.onvif.org/ver10/events/wsdl\"><Timeout>PT8S</Timeout><MessageLimit>1</MessageLimit></PullMessages>";
            case GetEventProperties:
                return "<GetEventProperties xmlns=\"http://www.onvif.org/ver10/events/wsdl\"/>";
            case RelativeMoveLeft:
                return "<RelativeMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex)
                        + "</ProfileToken><Translation><PanTilt x=\"0.05000000\" y=\"0\" xmlns=\"http://www.onvif.org/ver10/schema\"/></Translation></RelativeMove>";
            case RelativeMoveRight:
                return "<RelativeMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex)
                        + "</ProfileToken><Translation><PanTilt x=\"-0.05000000\" y=\"0\" xmlns=\"http://www.onvif.org/ver10/schema\"/></Translation></RelativeMove>";
            case RelativeMoveUp:
                return "<RelativeMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex)
                        + "</ProfileToken><Translation><PanTilt x=\"0\" y=\"0.100000000\" xmlns=\"http://www.onvif.org/ver10/schema\"/></Translation></RelativeMove>";
            case RelativeMoveDown:
                return "<RelativeMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex)
                        + "</ProfileToken><Translation><PanTilt x=\"0\" y=\"-0.100000000\" xmlns=\"http://www.onvif.org/ver10/schema\"/></Translation></RelativeMove>";
            case RelativeMoveIn:
                return "<RelativeMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex)
                        + "</ProfileToken><Translation><Zoom x=\"0.0240506344\" xmlns=\"http://www.onvif.org/ver10/schema\"/></Translation></RelativeMove>";
            case RelativeMoveOut:
                return "<RelativeMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex)
                        + "</ProfileToken><Translation><Zoom x=\"-0.0240506344\" xmlns=\"http://www.onvif.org/ver10/schema\"/></Translation></RelativeMove>";
            case Renew:
                return "<Renew xmlns=\"http://docs.oasis-open.org/wsn/b-2\"><TerminationTime>PT1M</TerminationTime></Renew>";
            case GetConfigurations:
                return "<GetConfigurations xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"></GetConfigurations>";
            case GetConfigurationOptions:
                return "<GetConfigurationOptions xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ConfigurationToken>"
                        + ptzConfigToken + "</ConfigurationToken></GetConfigurationOptions>";
            case GetConfiguration:
                return "<GetConfiguration xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><PTZConfigurationToken>"
                        + ptzConfigToken + "</PTZConfigurationToken></GetConfiguration>";
            case SetConfiguration:// not tested to work yet
                return "<SetConfiguration xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><PTZConfiguration><NodeToken>"
                        + ptzNodeToken
                        + "</NodeToken><DefaultAbsolutePantTiltPositionSpace>AbsolutePanTiltPositionSpace</DefaultAbsolutePantTiltPositionSpace><DefaultAbsoluteZoomPositionSpace>AbsoluteZoomPositionSpace</DefaultAbsoluteZoomPositionSpace></PTZConfiguration></SetConfiguration>";
            case GetNodes:
                return "<GetNodes xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"></GetNodes>";
            case GetStatus:
                return "<GetStatus xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex) + "</ProfileToken></GetStatus>";
            case GotoPreset:
                return "<GotoPreset xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex) + "</ProfileToken><PresetToken>"
                        + presetTokens.get(presetTokenIndex) + "</PresetToken></GotoPreset>";
            case GetPresets:
                return "<GetPresets xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\"><ProfileToken>"
                        + mediaProfileTokens.get(mediaProfileIndex) + "</ProfileToken></GetPresets>";
        }
        return "notfound";
    }

    public void processReply(String message) {
        log.trace("Onvif reply is:{}", message);
        if (message.contains("PullMessagesResponse")) {
            eventRecieved(message);
        } else if (message.contains("RenewResponse")) {
            sendOnvifRequest(requestBuilder(RequestType.PullMessages, subscriptionXAddr));
        } else if (message.contains("GetSystemDateAndTimeResponse")) {// 1st to be sent.
            isConnected = true;
            sendOnvifRequest(requestBuilder(RequestType.GetCapabilities, deviceXAddr));
            parseDateAndTime(message);
            log.debug("Openhabs UTC dateTime is:{}", getUTCdateTime());
        } else if (message.contains("GetCapabilitiesResponse")) {// 2nd to be sent.
            parseXAddr(message);
            sendOnvifRequest(requestBuilder(RequestType.GetProfiles, mediaXAddr));
        } else if (message.contains("GetProfilesResponse")) {// 3rd to be sent.
            parseProfiles(message);
            sendOnvifRequest(requestBuilder(RequestType.GetSnapshotUri, mediaXAddr));
            sendOnvifRequest(requestBuilder(RequestType.GetStreamUri, mediaXAddr));
            if (ptzDevice) {
                sendPTZRequest(RequestType.GetNodes);
            }
            if (usingEvents) {// stops API cameras from getting sent ONVIF events.
                sendOnvifRequest(requestBuilder(RequestType.GetEventProperties, eventXAddr));
                sendOnvifRequest(requestBuilder(RequestType.GetServiceCapabilities, eventXAddr));
            }
        } else if (message.contains("GetServiceCapabilitiesResponse")) {
            if (message.contains("WSSubscriptionPolicySupport=\"true\"")) {
                sendOnvifRequest(requestBuilder(RequestType.Subscribe, eventXAddr));
            }
        } else if (message.contains("GetEventPropertiesResponse")) {
            sendOnvifRequest(requestBuilder(RequestType.CreatePullPointSubscription, eventXAddr));
        } else if (message.contains("SubscribeResponse")) {
            log.info("Onvif Subscribe appears to be working for Alarms/Events.");
        } else if (message.contains("CreatePullPointSubscriptionResponse")) {
            subscriptionXAddr = removeIPfromUrl(Helper.fetchXML(message, "SubscriptionReference>", "Address>"));
            log.debug("subscriptionXAddr={}", subscriptionXAddr);
            sendOnvifRequest(requestBuilder(RequestType.PullMessages, subscriptionXAddr));
        } else if (message.contains("GetStatusResponse")) {
            processPTZLocation(message);
        } else if (message.contains("GetPresetsResponse")) {
            parsePresets(message);
        } else if (message.contains("GetConfigurationsResponse")) {
            sendPTZRequest(RequestType.GetPresets);
            ptzConfigToken = Helper.fetchXML(message, "PTZConfiguration", "token=\"");
            log.debug("ptzConfigToken={}", ptzConfigToken);
            sendPTZRequest(RequestType.GetConfigurationOptions);
        } else if (message.contains("GetNodesResponse")) {
            sendPTZRequest(RequestType.GetStatus);
            ptzNodeToken = Helper.fetchXML(message, "", "token=\"");
            log.debug("ptzNodeToken={}", ptzNodeToken);
            sendPTZRequest(RequestType.GetConfigurations);
        } else if (message.contains("GetDeviceInformationResponse")) {
            log.debug("GetDeviceInformationResponse recieved");
        } else if (message.contains("GetSnapshotUriResponse")) {
            snapshotUri = removeIPfromUrl(Helper.fetchXML(message, ":MediaUri", ":Uri"));
            log.debug("GetSnapshotUri:{}", snapshotUri);
            if (ipCameraHandler.snapshotUri.isEmpty()) {
                ipCameraHandler.snapshotUri = snapshotUri;
            }
        } else if (message.contains("GetStreamUriResponse")) {
            rtspUri = Helper.fetchXML(message, ":MediaUri", ":Uri>");
            log.debug("GetStreamUri:{}", rtspUri);
            if (ipCameraHandler.getCameraEntity().getFfmpegInput().isEmpty()) {
                ipCameraHandler.rtspUri = rtspUri;
            }
        }
    }

    HttpRequest requestBuilder(RequestType requestType, String xAddr) {
        log.trace("Sending ONVIF request:{}", requestType);
        String security;
        String extraEnvelope = "";
        String headerTo = "";
        String getXmlCache = getXml(requestType);
        if (requestType.equals(RequestType.CreatePullPointSubscription) || requestType.equals(RequestType.PullMessages)
                || requestType.equals(RequestType.Renew) || requestType.equals(RequestType.Unsubscribe)) {
            headerTo = "<a:To s:mustUnderstand=\"1\">http://" + ipAddress + xAddr + "</a:To>";
            extraEnvelope = " xmlns:a=\"http://www.w3.org/2005/08/addressing\"";
        }
        String headers;
        if (!password.isEmpty() && !requestType.equals(RequestType.GetSystemDateAndTime)) {
            String nonce = createNonce();
            String dateTime = getUTCdateTime();
            String digest = createDigest(nonce, dateTime);
            security = "<Security s:mustUnderstand=\"1\" xmlns=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\"><UsernameToken><Username>"
                    + user
                    + "</Username><Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">"
                    + digest
                    + "</Password><Nonce EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\">"
                    + encodeBase64(nonce)
                    + "</Nonce><Created xmlns=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">"
                    + dateTime + "</Created></UsernameToken></Security>";
            headers = "<s:Header>" + security + headerTo + "</s:Header>";
        } else {// GetSystemDateAndTime must not be password protected as per spec.
            headers = "";
        }
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod("POST"), xAddr);
        String actionString = Helper.fetchXML(getXmlCache, requestType.toString(), "xmlns=\"");
        request.headers().add("Content-Type",
                "application/soap+xml; charset=utf-8; action=\"" + actionString + "/" + requestType + "\"");
        request.headers().add("Charset", "utf-8");
        if (onvifPort != 80) {
            request.headers().set("Host", ipAddress + ":" + onvifPort);
        } else {
            request.headers().set("Host", ipAddress);
        }
        request.headers().set("Connection", HttpHeaderValues.CLOSE);
        request.headers().set("Accept-Encoding", "gzip, deflate");
        String fullXml = "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"" + extraEnvelope + ">"
                + headers
                + "<s:Body xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">"
                + getXmlCache + "</s:Body></s:Envelope>";
        request.headers().add("SOAPAction", "\"" + actionString + "/" + requestType + "\"");
        ByteBuf bbuf = Unpooled.copiedBuffer(fullXml, StandardCharsets.UTF_8);
        request.headers().set("Content-Length", bbuf.readableBytes());
        request.content().clear().writeBytes(bbuf);
        return request;
    }

    /**
     * The removeIPfromUrl Will throw away all text before the cameras IP, also removes the IP and the PORT
     * leaving just the URL.
     *
     * @author Matthew Skinner - Initial contribution
     */
    String removeIPfromUrl(String url) {
        int index = url.indexOf("//");
        if (index != -1) {// now remove the :port
            index = url.indexOf("/", index + 2);
        }
        if (index == -1) {
            log.debug("We hit an issue parsing url:{}", url);
            return "";
        }
        return url.substring(index);
    }

    void parseXAddr(String message) {
        // Normally I would search '<tt:XAddr>' instead but Foscam needed this work around.
        String temp = removeIPfromUrl(Helper.fetchXML(message, "<tt:Device", "tt:XAddr"));
        if (!temp.isEmpty()) {
            deviceXAddr = temp;
            log.debug("deviceXAddr:{}", deviceXAddr);
        }
        temp = removeIPfromUrl(Helper.fetchXML(message, "<tt:Events", "tt:XAddr"));
        if (!temp.isEmpty()) {
            subscriptionXAddr = eventXAddr = temp;
            log.debug("eventsXAddr:{}", eventXAddr);
        }
        temp = removeIPfromUrl(Helper.fetchXML(message, "<tt:Media", "tt:XAddr"));
        if (!temp.isEmpty()) {
            mediaXAddr = temp;
            log.debug("mediaXAddr:{}", mediaXAddr);
        }

        ptzXAddr = removeIPfromUrl(Helper.fetchXML(message, "<tt:PTZ", "tt:XAddr"));
        if (ptzXAddr.isEmpty()) {
            ptzDevice = false;
            log.trace("Camera must not support PTZ, it failed to give a <tt:PTZ><tt:XAddr>:{}", message);
        } else {
            log.debug("ptzXAddr:{}", ptzXAddr);
        }
    }

    private void parseDateAndTime(String message) {
        String minute = Helper.fetchXML(message, "UTCDateTime", "Minute>");
        String hour = Helper.fetchXML(message, "UTCDateTime", "Hour>");
        String second = Helper.fetchXML(message, "UTCDateTime", "Second>");
        String day = Helper.fetchXML(message, "UTCDateTime", "Day>");
        String month = Helper.fetchXML(message, "UTCDateTime", "Month>");
        String year = Helper.fetchXML(message, "UTCDateTime", "Year>");
        log.debug("Cameras  UTC dateTime is:{}-{}-{}T{}:{}:{}", year, month, day, hour, minute, second);
    }

    private String getUTCdateTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    String createNonce() {
        Random nonce = new Random();
        return "" + nonce.nextInt();
    }

    String encodeBase64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }

    String createDigest(String nOnce, String dateTime) {
        String beforeEncryption = nOnce + dateTime + password;
        MessageDigest msgDigest;
        byte[] encryptedRaw = null;
        try {
            msgDigest = MessageDigest.getInstance("SHA-1");
            msgDigest.reset();
            msgDigest.update(beforeEncryption.getBytes(StandardCharsets.UTF_8));
            encryptedRaw = msgDigest.digest();
        } catch (NoSuchAlgorithmException ignored) {
        }
        return Base64.getEncoder().encodeToString(encryptedRaw);
    }

    @SuppressWarnings("null")
    public void sendOnvifRequest(HttpRequest request) {
        if (bootstrap == null) {
            bootstrap = new Bootstrap();
            bootstrap.group(mainEventLoopGroup);
            bootstrap.channel(NioSocketChannel.class);
            bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
            bootstrap.option(ChannelOption.SO_SNDBUF, 1024 * 8);
            bootstrap.option(ChannelOption.SO_RCVBUF, 1024 * 1024);
            bootstrap.option(ChannelOption.TCP_NODELAY, true);
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {

                @Override
                public void initChannel(SocketChannel socketChannel) {
                    socketChannel.pipeline().addLast("idleStateHandler", new IdleStateHandler(0, 0, 70));
                    socketChannel.pipeline().addLast("HttpClientCodec", new HttpClientCodec());
                    socketChannel.pipeline().addLast("OnvifCodec", new OnvifCodec(getHandle()));
                }
            });
        }
        bootstrap.connect(new InetSocketAddress(ipAddress, onvifPort)).addListener((ChannelFutureListener) future -> {
            if (future == null) {
                return;
            }
            if (future.isDone() && future.isSuccess()) {
                Channel ch = future.channel();
                ch.writeAndFlush(request);
            } else { // an error occured
                log.debug("Camera is not reachable on ONVIF port:{} or the port may be wrong.", onvifPort);
                if (isConnected) {
                    disconnect();
                }
            }
        });
    }

    OnvifConnection getHandle() {
        return this;
    }

    void getIPandPortFromUrl(String url) {
        int beginIndex = url.indexOf(":");
        int endIndex = url.indexOf("/", beginIndex);
        if (beginIndex >= 0 && endIndex == -1) {// 192.168.1.1:8080
            ipAddress = url.substring(0, beginIndex);
            onvifPort = Integer.parseInt(url.substring(beginIndex + 1));
        } else if (beginIndex >= 0 && endIndex > beginIndex) {// 192.168.1.1:8080/foo/bar
            ipAddress = url.substring(0, beginIndex);
            onvifPort = Integer.parseInt(url.substring(beginIndex + 1, endIndex));
        } else {// 192.168.1.1
            ipAddress = url;
            log.debug("No Onvif Port found when parsing:{}", url);
        }
    }

    public void gotoPreset(int index) {
        if (ptzDevice) {
            if (index > 0) {// 0 is reserved for HOME as cameras seem to start at preset 1.
                if (presetTokens.isEmpty()) {
                    log.warn("Camera did not report any ONVIF preset locations, updating preset tokens now.");
                    sendPTZRequest(RequestType.GetPresets);
                } else {
                    presetTokenIndex = index - 1;
                    sendPTZRequest(RequestType.GotoPreset);
                }
            }
        }
    }

    public void eventRecieved(String eventMessage) {
        String topic = Helper.fetchXML(eventMessage, "Topic", "tns1:");
        String dataName = Helper.fetchXML(eventMessage, "tt:Data", "Name=\"");
        String dataValue = Helper.fetchXML(eventMessage, "tt:Data", "Value=\"");
        if (!topic.isEmpty()) {
            log.debug("Onvif Event Topic:{}, Data:{}, Value:{}", topic, dataName, dataValue);
        }
        switch (topic) {
            case "RuleEngine/CellMotionDetector/Motion":
                if (dataValue.equals("true")) {
                    ipCameraHandler.motionDetected(CHANNEL_CELL_MOTION_ALARM);
                } else if (dataValue.equals("false")) {
                    ipCameraHandler.noMotionDetected(CHANNEL_CELL_MOTION_ALARM);
                }
                break;
            case "VideoSource/MotionAlarm":
                if (dataValue.equals("true")) {
                    ipCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
                } else if (dataValue.equals("false")) {
                    ipCameraHandler.noMotionDetected(CHANNEL_MOTION_ALARM);
                }
                break;
            case "AudioAnalytics/Audio/DetectedSound":
                if (dataValue.equals("true")) {
                    ipCameraHandler.audioDetected();
                } else if (dataValue.equals("false")) {
                    ipCameraHandler.noAudioDetected();
                }
                break;
            case "RuleEngine/FieldDetector/ObjectsInside":
                if (dataValue.equals("true")) {
                    ipCameraHandler.motionDetected(CHANNEL_FIELD_DETECTION_ALARM);
                } else if (dataValue.equals("false")) {
                    ipCameraHandler.noMotionDetected(CHANNEL_FIELD_DETECTION_ALARM);
                }
                break;
            case "RuleEngine/LineDetector/Crossed":
                if (dataName.equals("ObjectId")) {
                    ipCameraHandler.motionDetected(CHANNEL_LINE_CROSSING_ALARM);
                } else {
                    ipCameraHandler.noMotionDetected(CHANNEL_LINE_CROSSING_ALARM);
                }
                break;
            case "RuleEngine/TamperDetector/Tamper":
                if (dataValue.equals("true")) {
                    ipCameraHandler.changeAlarmState(CHANNEL_TAMPER_ALARM, OnOffType.ON);
                } else if (dataValue.equals("false")) {
                    ipCameraHandler.changeAlarmState(CHANNEL_TAMPER_ALARM, OnOffType.OFF);
                }
                break;
            case "Device/HardwareFailure/StorageFailure":
                if (dataValue.equals("true")) {
                    ipCameraHandler.changeAlarmState(CHANNEL_STORAGE_ALARM, OnOffType.ON);
                } else if (dataValue.equals("false")) {
                    ipCameraHandler.changeAlarmState(CHANNEL_STORAGE_ALARM, OnOffType.OFF);
                }
                break;
            case "VideoSource/ImageTooDark/AnalyticsService":
            case "VideoSource/ImageTooDark/ImagingService":
            case "VideoSource/ImageTooDark/RecordingService":
                if (dataValue.equals("true")) {
                    ipCameraHandler.changeAlarmState(CHANNEL_TOO_DARK_ALARM, OnOffType.ON);
                } else if (dataValue.equals("false")) {
                    ipCameraHandler.changeAlarmState(CHANNEL_TOO_DARK_ALARM, OnOffType.OFF);
                }
                break;
            case "VideoSource/GlobalSceneChange/AnalyticsService":
            case "VideoSource/GlobalSceneChange/ImagingService":
            case "VideoSource/GlobalSceneChange/RecordingService":
                if (dataValue.equals("true")) {
                    ipCameraHandler.changeAlarmState(CHANNEL_SCENE_CHANGE_ALARM, OnOffType.ON);
                } else if (dataValue.equals("false")) {
                    ipCameraHandler.changeAlarmState(CHANNEL_SCENE_CHANGE_ALARM, OnOffType.OFF);
                }
                break;
            case "VideoSource/ImageTooBright/AnalyticsService":
            case "VideoSource/ImageTooBright/ImagingService":
            case "VideoSource/ImageTooBright/RecordingService":
                if (dataValue.equals("true")) {
                    ipCameraHandler.changeAlarmState(CHANNEL_TOO_BRIGHT_ALARM, OnOffType.ON);
                } else if (dataValue.equals("false")) {
                    ipCameraHandler.changeAlarmState(CHANNEL_TOO_BRIGHT_ALARM, OnOffType.OFF);
                }
                break;
            case "VideoSource/ImageTooBlurry/AnalyticsService":
            case "VideoSource/ImageTooBlurry/ImagingService":
            case "VideoSource/ImageTooBlurry/RecordingService":
                if (dataValue.equals("true")) {
                    ipCameraHandler.changeAlarmState(CHANNEL_TOO_BLURRY_ALARM, OnOffType.ON);
                } else if (dataValue.equals("false")) {
                    ipCameraHandler.changeAlarmState(CHANNEL_TOO_BLURRY_ALARM, OnOffType.OFF);
                }
                break;
            default:
        }
        sendOnvifRequest(requestBuilder(RequestType.Renew, subscriptionXAddr));
    }

    public boolean supportsPTZ() {
        return ptzDevice;
    }

    public void getStatus() {
        if (ptzDevice) {
            sendPTZRequest(RequestType.GetStatus);
        }
    }

    public Float getAbsolutePan() {
        return currentPanPercentage;
    }

    public Float getAbsoluteTilt() {
        return currentTiltPercentage;
    }

    public Float getAbsoluteZoom() {
        return currentZoomPercentage;
    }

    public void setAbsolutePan(Float panValue) {// Value is 0-100% of cameras range
        if (ptzDevice) {
            currentPanPercentage = panValue;
            currentPanCamValue = ((((panRangeMin - panRangeMax) * -1) / 100) * panValue + panRangeMin);
        }
    }

    public void setAbsoluteTilt(Float tiltValue) {// Value is 0-100% of cameras range
        if (ptzDevice) {
            currentTiltPercentage = tiltValue;
            currentTiltCamValue = ((((panRangeMin - panRangeMax) * -1) / 100) * tiltValue + tiltRangeMin);
        }
    }

    public void setAbsoluteZoom(Float zoomValue) {// Value is 0-100% of cameras range
        if (ptzDevice) {
            currentZoomPercentage = zoomValue;
            currentZoomCamValue = ((((zoomMin - zoomMax) * -1) / 100) * zoomValue + zoomMin);
        }
    }

    public void absoluteMove() { // Camera wont move until PTZ values are set, then call this.
        if (ptzDevice) {
            sendPTZRequest(RequestType.AbsoluteMove);
        }
    }

    public void setSelectedMediaProfile(int mediaProfileIndex) {
        this.mediaProfileIndex = mediaProfileIndex;
    }

    List<String> listOfResults(String message, String heading, String key) {
        List<String> results = new LinkedList<>();
        String temp;
        for (int startLookingFromIndex = 0; startLookingFromIndex != -1; ) {
            startLookingFromIndex = message.indexOf(heading, startLookingFromIndex);
            if (startLookingFromIndex >= 0) {
                temp = Helper.fetchXML(message.substring(startLookingFromIndex), heading, key);
                if (!temp.isEmpty()) {
                    log.trace("String was found:{}", temp);
                    results.add(temp);
                } else {
                    return results;// key string must not exist so stop looking.
                }
                startLookingFromIndex += temp.length();
            }
        }
        return results;
    }

    void parsePresets(String message) {
        List<Pair<String, String>> presets = new ArrayList<>();
        int counter = 1;// Presets start at 1 not 0. HOME may be added to index 0.
        presetTokens = listOfResults(message, "<tptz:Preset", "token=\"");
        presetNames = listOfResults(message, "<tptz:Preset", "<tt:Name>");
        if (presetTokens.size() != presetNames.size()) {
            log.warn("Camera did noStateOptiont report the same number of Tokens and Names for PTZ presets");
            return;
        }
        for (String value : presetNames) {
            presets.add(Pair.of(Integer.toString(counter++), value));
        }
        /*ipCameraHandler.stateDescriptionProvider
                .setStateOptions(new ChannelUID(ipCameraHandler.getThing().getUID(), CHANNEL_GOTO_PRESET), presets);*/
    }

    void parseProfiles(String message) {
        mediaProfileTokens = listOfResults(message, "<trt:Profiles", "token=\"");
        if (mediaProfileIndex >= mediaProfileTokens.size()) {
            log.warn(
                    "You have set the media profile to {} when the camera reported {} profiles. Falling back to mainstream 0.",
                    mediaProfileIndex, mediaProfileTokens.size());
            mediaProfileIndex = 0;
        }
    }

    void processPTZLocation(String result) {
        log.debug("Processing new PTZ location now");

        int beginIndex = result.indexOf("x=\"");
        int endIndex = result.indexOf("\"", (beginIndex + 3));
        if (beginIndex >= 0 && endIndex >= 0) {
            currentPanCamValue = Float.parseFloat(result.substring(beginIndex + 3, endIndex));
            currentPanPercentage = (((panRangeMin - currentPanCamValue) * -1) / ((panRangeMin - panRangeMax) * -1))
                    * 100;
            log.debug("Pan is updating to:{} and the cam value is {}", Math.round(currentPanPercentage),
                    currentPanCamValue);
        } else {
            log.warn(
                    "Binding could not determin the cameras current PTZ location. Not all cameras respond to GetStatus requests.");
            return;
        }

        beginIndex = result.indexOf("y=\"");
        endIndex = result.indexOf("\"", (beginIndex + 3));
        if (beginIndex >= 0 && endIndex >= 0) {
            currentTiltCamValue = Float.parseFloat(result.substring(beginIndex + 3, endIndex));
            currentTiltPercentage = (((tiltRangeMin - currentTiltCamValue) * -1) / ((tiltRangeMin - tiltRangeMax) * -1))
                    * 100;
            log.debug("Tilt is updating to:{} and the cam value is {}", Math.round(currentTiltPercentage),
                    currentTiltCamValue);
        } else {
            return;
        }

        beginIndex = result.lastIndexOf("x=\"");
        endIndex = result.indexOf("\"", (beginIndex + 3));
        if (beginIndex >= 0 && endIndex >= 0) {
            currentZoomCamValue = Float.parseFloat(result.substring(beginIndex + 3, endIndex));
            currentZoomPercentage = (((zoomMin - currentZoomCamValue) * -1) / ((zoomMin - zoomMax) * -1)) * 100;
            log.debug("Zoom is updating to:{} and the cam value is {}", Math.round(currentZoomPercentage),
                    currentZoomCamValue);
        }
    }

    public void sendPTZRequest(RequestType requestType) {
        sendOnvifRequest(requestBuilder(requestType, ptzXAddr));
    }

    public void sendEventRequest(RequestType requestType) {
        sendOnvifRequest(requestBuilder(requestType, eventXAddr));
    }

    public void connect(boolean useEvents) {
        if (!isConnected) {
            sendOnvifRequest(requestBuilder(RequestType.GetSystemDateAndTime, deviceXAddr));
            usingEvents = useEvents;
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void disconnect() {
        if (usingEvents && isConnected) {
            sendOnvifRequest(requestBuilder(RequestType.Unsubscribe, subscriptionXAddr));
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
        }
        isConnected = false;
        presetTokens.clear();
        mediaProfileTokens.clear();
        if (!mainEventLoopGroup.isShutdown()) {
            try {
                mainEventLoopGroup.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.info("Onvif was not shutdown correctly due to being interrupted");
            } finally {
                mainEventLoopGroup = new NioEventLoopGroup();
                bootstrap = null;
            }
        }
    }
}
