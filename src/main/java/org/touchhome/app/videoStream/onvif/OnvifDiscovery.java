package org.touchhome.app.videoStream.onvif;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.touchhome.app.videoStream.entity.OnvifCameraType;
import org.touchhome.app.videoStream.onvif.util.Helper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * responsible for finding cameras that are ONVIF using UDP multicast.
 */
@Log4j2
@RequiredArgsConstructor
public class OnvifDiscovery {
    private ArrayList<DatagramPacket> listOfReplays = new ArrayList<>(2);

    public void discoverCameras(CameraFoundHandler cameraFoundHandler) throws UnknownHostException, InterruptedException {
        List<NetworkInterface> nics = getLocalNICs();
        if (nics.isEmpty()) {
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

    public interface CameraFoundHandler {
        void handle(OnvifCameraType brand, String ipAddress, int onvifPort);
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
        OnvifCameraType brand = checkForBrand(xml);
        if (brand == OnvifCameraType.onvif) {
            try {
                brand = getBrandFromLoginPage(ipAddress);
            } catch (IOException ignore) {
            }
        }
        cameraFoundHandler.handle(brand, ipAddress, onvifPort.intValue());
    }

    private void processCameraRepays(CameraFoundHandler cameraFoundHandler) {
        for (DatagramPacket packet : listOfReplays) {
            log.trace("Device replied to discovery with:{}", packet.toString());
            String xml = packet.content().toString(CharsetUtil.UTF_8);
            String xAddr = Helper.fetchXML(xml, "", "<d:XAddrs>");
            if (!xAddr.equals("")) {
                searchReply(cameraFoundHandler, xAddr, xml);
            } else if (xml.contains("onvif")) {
                log.info("Possible ONVIF camera found at:{}", packet.sender().getHostString());
                cameraFoundHandler.handle(OnvifCameraType.onvif, packet.sender().getHostString(), 80);
            }
        }
    }

    private OnvifCameraType checkForBrand(String response) {
        if (response.toLowerCase().contains("amcrest")) {
            return OnvifCameraType.dahua;
        } else if (response.toLowerCase().contains("dahua")) {
            return OnvifCameraType.dahua;
        } else if (response.toLowerCase().contains("foscam")) {
            return OnvifCameraType.foscam;
        } else if (response.toLowerCase().contains("hikvision")) {
            return OnvifCameraType.hikvision;
        } else if (response.toLowerCase().contains("instar")) {
            return OnvifCameraType.instar;
        } else if (response.toLowerCase().contains("doorbird")) {
            return OnvifCameraType.doorbird;
        } else if (response.toLowerCase().contains("ipc-")) {
            return OnvifCameraType.dahua;
        } else if (response.toLowerCase().contains("dh-sd")) {
            return OnvifCameraType.dahua;
        }
        return OnvifCameraType.onvif;
    }

    private OnvifCameraType getBrandFromLoginPage(String hostname) throws IOException {
        URL url = new URL("http://" + hostname);
        OnvifCameraType brand = OnvifCameraType.onvif;
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(2000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");
        try {
            connection.connect();
            BufferedReader reply = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String temp;
            while ((temp = reply.readLine()) != null) {
                response.append(temp);
            }
            reply.close();
            log.trace("Cameras Login page is:{}", response.toString());
            brand = checkForBrand(response.toString());
        } catch (MalformedURLException ignored) {
        } finally {
            connection.disconnect();
        }
        return brand;
    }

    private DatagramPacket wsDiscovery() throws UnknownHostException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><e:Envelope xmlns:e=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:w=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" xmlns:dn=\"http://www.onvif.org/ver10/network/wsdl\"><e:Header><w:MessageID>uuid:"
                + UUID.randomUUID()
                + "</w:MessageID><w:To e:mustUnderstand=\"true\">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To><w:Action a:mustUnderstand=\"true\">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action></e:Header><e:Body><d:Probe><d:Types xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" xmlns:dp0=\"http://www.onvif.org/ver10/network/wsdl\">dp0:NetworkVideoTransmitter</d:Types></d:Probe></e:Body></e:Envelope>";
        ByteBuf discoveryProbeMessage = Unpooled.copiedBuffer(xml, 0, xml.length(), StandardCharsets.UTF_8);
        return new DatagramPacket(discoveryProbeMessage,
                new InetSocketAddress(InetAddress.getByName("239.255.255.250"), 3702), new InetSocketAddress(0));
    }
}
