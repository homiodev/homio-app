package org.touchhome.app.camera.openhub.onvif;

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
import lombok.extern.log4j.Log4j2;
import org.touchhome.app.camera.openhub.Helper;
import org.touchhome.app.camera.openhub.IpCameraDiscoveryService;

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
public class OnvifDiscovery {

    private IpCameraDiscoveryService ipCameraDiscoveryService;
    public ArrayList<DatagramPacket> listOfReplys = new ArrayList<>(10);

    public OnvifDiscovery(IpCameraDiscoveryService ipCameraDiscoveryService) {
        this.ipCameraDiscoveryService = ipCameraDiscoveryService;
    }

    public List<NetworkInterface> getLocalNICs() {
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

    void searchReply(String url, String xml) {
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
        String brand = checkForBrand(xml);
        if (brand.equals("onvif")) {
            try {
                brand = getBrandFromLoginPage(ipAddress);
            } catch (IOException e) {
                brand = "onvif";
            }
        }
        ipCameraDiscoveryService.newCameraFound(brand, ipAddress, onvifPort.intValue());
    }

    void processCameraReplys() {
        for (DatagramPacket packet : listOfReplys) {
            log.trace("Device replied to discovery with:{}", packet.toString());
            String xml = packet.content().toString(CharsetUtil.UTF_8);
            String xAddr = Helper.fetchXML(xml, "", "<d:XAddrs>");
            if (!xAddr.equals("")) {
                searchReply(xAddr, xml);
            } else if (xml.contains("onvif")) {
                log.info("Possible ONVIF camera found at:{}", packet.sender().getHostString());
                ipCameraDiscoveryService.newCameraFound("onvif", packet.sender().getHostString(), 80);
            }
        }
    }

    String checkForBrand(String response) {
        if (response.toLowerCase().contains("amcrest")) {
            return "dahua";
        } else if (response.toLowerCase().contains("dahua")) {
            return "dahua";
        } else if (response.toLowerCase().contains("foscam")) {
            return "foscam";
        } else if (response.toLowerCase().contains("hikvision")) {
            return "hikvision";
        } else if (response.toLowerCase().contains("instar")) {
            return "instar";
        } else if (response.toLowerCase().contains("doorbird")) {
            return "doorbird";
        } else if (response.toLowerCase().contains("ipc-")) {
            return "dahua";
        } else if (response.toLowerCase().contains("dh-sd")) {
            return "dahua";
        }
        return "onvif";
    }

    public String getBrandFromLoginPage(String hostname) throws IOException {
        URL url = new URL("http://" + hostname);
        String brand = "onvif";
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

    public void discoverCameras() throws UnknownHostException, InterruptedException {
        List<NetworkInterface> nics = getLocalNICs();
        if (nics == null || nics.isEmpty()) {
            return;
        }
        NetworkInterface networkInterface = nics.get(0);
        Bootstrap bootstrap = new Bootstrap().group(new NioEventLoopGroup())
                .channelFactory(new ChannelFactory<NioDatagramChannel>() {
                    @Override
                    public NioDatagramChannel newChannel() {
                        return new NioDatagramChannel(InternetProtocolFamily.IPv4);
                    }
                }).handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                        msg.retain(1);
                        listOfReplys.add(msg);
                    }
                }).option(ChannelOption.SO_BROADCAST, true).option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.IP_MULTICAST_LOOP_DISABLED, false).option(ChannelOption.SO_RCVBUF, 2048)
                .option(ChannelOption.IP_MULTICAST_TTL, 255).option(ChannelOption.IP_MULTICAST_IF, networkInterface);
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
            processCameraReplys();
            bootstrap.config().group().shutdownGracefully();
        }
    }
}
