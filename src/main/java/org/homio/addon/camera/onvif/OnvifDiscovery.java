package org.homio.addon.camera.onvif;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.homio.addon.camera.onvif.brand.CameraBrandHandlerDescription;
import org.homio.addon.camera.onvif.util.Helper;
import org.homio.addon.camera.service.IpCameraService;
import org.homio.api.EntityContext;

/**
 * responsible for finding cameras that are ONVIF using UDP multicast.
 */
@Log4j2
@RequiredArgsConstructor
public class OnvifDiscovery {

    private final ArrayList<DatagramPacket> listOfReplays = new ArrayList<>(2);
    private final EntityContext entityContext;

    private static CameraBrandHandlerDescription checkForBrand(String response, EntityContext entityContext) {
        for (CameraBrandHandlerDescription brandHandler : IpCameraService.getCameraBrands(entityContext).values()) {
            if (response.contains(brandHandler.getName())) {
                return brandHandler;
            }
        }
        return CameraBrandHandlerDescription.DEFAULT_BRAND;
    }

    public static CameraBrandHandlerDescription getBrandFromLoginPage(String hostname, EntityContext entityContext) {
        try {
            URL url = new URL("http://" + hostname);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(2000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            try {
                connection.connect();
                String response = IOUtils.toString(connection.getInputStream(), Charset.defaultCharset());
                return checkForBrand(response, entityContext);
            } catch (MalformedURLException ignored) {
            } finally {
                connection.disconnect();
            }
        } catch (Exception ignore) {
        }
        return CameraBrandHandlerDescription.DEFAULT_BRAND;
    }

    public void discoverCameras(CameraFoundHandler cameraFoundHandler) throws UnknownHostException, InterruptedException {
        List<NetworkInterface> nics = getLocalNICs();
        if (nics.isEmpty()) {
            log.warn("No 'Primary Address' selected to use for camera discovery");
            return;
        }
        NetworkInterface networkInterface = nics.get(0);
        SimpleChannelInboundHandler<DatagramPacket> handler = new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                msg.retain(1);
                listOfReplays.add(msg);
            }

            @Override
            public boolean isSharable() {
                return true;
            }
        };
        Bootstrap bootstrap = new Bootstrap().group(new NioEventLoopGroup())
                .channelFactory((ChannelFactory<NioDatagramChannel>) () -> new NioDatagramChannel(InternetProtocolFamily.IPv4)).handler(handler)
                .option(ChannelOption.SO_BROADCAST, true)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.IP_MULTICAST_LOOP_DISABLED, false)
                .option(ChannelOption.SO_RCVBUF, 2048)
                .option(ChannelOption.IP_MULTICAST_TTL, 255)
                .option(ChannelOption.IP_MULTICAST_IF, networkInterface);
        ChannelGroup openChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        for (NetworkInterface nic : nics) {
            DatagramChannel datagramChannel = (DatagramChannel) bootstrap.option(ChannelOption.IP_MULTICAST_IF, nic)
                    .bind(new InetSocketAddress(0)).sync().channel();
            datagramChannel
                    .joinGroup(new InetSocketAddress(InetAddress.getByName("239.255.255.250"), 3702), networkInterface)
                    .sync();
            openChannels.add(datagramChannel);
        }
        if (!openChannels.isEmpty()) {
            openChannels.writeAndFlush(wsDiscovery());
            TimeUnit.SECONDS.sleep(6);
            openChannels.close();
            processCameraRepays(cameraFoundHandler);
            bootstrap.config().group().shutdownGracefully();
        }
    }

    private List<NetworkInterface> getLocalNICs() {
        List<NetworkInterface> results = new ArrayList<>(2);
        try {
            for (Enumeration<NetworkInterface> enumNetworks = NetworkInterface.getNetworkInterfaces(); enumNetworks
                    .hasMoreElements(); ) {
                NetworkInterface networkInterface = enumNetworks.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses(); enumIpAddr
                        .hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getHostAddress().length() < 18
                            && inetAddress.isSiteLocalAddress()) {
                        results.add(networkInterface);
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return results;
    }

    private void searchReply(CameraFoundHandler cameraFoundHandler, String url, String xml) {
        String ipAddress;
        String temp = url;
        BigDecimal onvifPort = new BigDecimal(80);

        log.info("Camera found at xAddr:{}", url);
        int endIndex = temp.indexOf(" ");// Some xAddr have two urls with a space in between.
        if (endIndex > 0) {
            temp = temp.substring(0, endIndex);// Use only the first url from now on.
        }

        int beginIndex = temp.indexOf(":") + 3;// add 3 to ignore the :// after http.
        int secondIndex = temp.indexOf(":", beginIndex); // find second :
        endIndex = temp.indexOf("/", beginIndex);
        if (secondIndex > beginIndex && endIndex > secondIndex) {// http://192.168.0.1:8080/onvif/device_service
            ipAddress = temp.substring(beginIndex, secondIndex);
            onvifPort = new BigDecimal(temp.substring(secondIndex + 1, endIndex));
        } else {// // http://192.168.0.1/onvif/device_service
            ipAddress = temp.substring(beginIndex, endIndex);
        }
        CameraBrandHandlerDescription brand = checkForBrand(xml, entityContext);
        if (brand.getID().equals(CameraBrandHandlerDescription.DEFAULT_BRAND.getID())) {
            brand = getBrandFromLoginPage(ipAddress, entityContext);
        }
        cameraFoundHandler.handle(brand, ipAddress, onvifPort.intValue());
    }

    private void processCameraRepays(CameraFoundHandler cameraFoundHandler) {
        for (DatagramPacket packet : listOfReplays) {
            String xml = packet.content().toString(CharsetUtil.UTF_8);

            log.trace("Device replied to discovery with:{}", packet.toString());
            String xAddr = Helper.fetchXML(xml, "", "<d:XAddrs>");
            if (!xAddr.equals("")) {
                searchReply(cameraFoundHandler, xAddr, xml);
            } else if (xml.contains("onvif")) {
                log.info("Possible ONVIF camera found at:{}", packet.sender().getHostString());
                cameraFoundHandler.handle(CameraBrandHandlerDescription.DEFAULT_BRAND, packet.sender().getHostString(), 80);
            }
        }
    }

    private DatagramPacket wsDiscovery() throws UnknownHostException {
        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><e:Envelope xmlns:e=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:w=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" "
                        + "xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" xmlns:dn=\"http://www.onvif.org/ver10/network/wsdl\"><e:Header><w:MessageID>uuid:"
                        + UUID.randomUUID()
                        + "</w:MessageID><w:To e:mustUnderstand=\"true\">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To><w:Action a:mustUnderstand=\"true\">http://schemas.xmlsoap"
                        + ".org/ws/2005/04/discovery/Probe</w:Action></e:Header><e:Body><d:Probe><d:Types xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" xmlns:dp0=\"http://www"
                        + ".onvif.org/ver10/network/wsdl\">dp0:NetworkVideoTransmitter</d:Types></d:Probe></e:Body></e:Envelope>";
        ByteBuf discoveryProbeMessage = Unpooled.copiedBuffer(xml, 0, xml.length(), StandardCharsets.UTF_8);
        return new DatagramPacket(discoveryProbeMessage,
                new InetSocketAddress(InetAddress.getByName("239.255.255.250"), 3702), new InetSocketAddress(0));
    }

    public interface CameraFoundHandler {

        void handle(CameraBrandHandlerDescription brand, String ipAddress, int onvifPort);
    }
}
