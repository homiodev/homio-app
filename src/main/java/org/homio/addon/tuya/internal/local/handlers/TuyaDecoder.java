package org.homio.addon.tuya.internal.local.handlers;

import static com.sshtools.common.util.Utils.bytesToHex;
import static org.homio.addon.tuya.internal.local.CommandType.BROADCAST_LPV34;
import static org.homio.addon.tuya.internal.local.CommandType.DP_QUERY;
import static org.homio.addon.tuya.internal.local.CommandType.DP_QUERY_NOT_SUPPORTED;
import static org.homio.addon.tuya.internal.local.CommandType.STATUS;
import static org.homio.addon.tuya.internal.local.CommandType.UDP;
import static org.homio.addon.tuya.internal.local.CommandType.UDP_NEW;
import static org.homio.addon.tuya.internal.local.ProtocolVersion.V3_3;
import static org.homio.addon.tuya.internal.local.ProtocolVersion.V3_4;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.internal.local.CommandType;
import org.homio.addon.tuya.internal.local.MessageWrapper;
import org.homio.addon.tuya.internal.local.ProtocolVersion;
import org.homio.addon.tuya.internal.local.TuyaDeviceCommunicator;
import org.homio.addon.tuya.internal.local.dto.DiscoveryMessage;
import org.homio.addon.tuya.internal.local.dto.TcpStatusPayload;
import org.homio.addon.tuya.internal.util.CryptoUtil;

/**
 * The {@link TuyaDecoder} is a Netty Decoder for encoding Tuya Local messages
 * Parts of this code are inspired by the TuyAPI project (see notice file)
 */
@Log4j2
public class TuyaDecoder extends ByteToMessageDecoder {

    private final TuyaDeviceCommunicator.KeyStore keyStore;
    private final ProtocolVersion version;
    private final Gson gson;
    private final String deviceId;

    public TuyaDecoder(Gson gson, String deviceId, TuyaDeviceCommunicator.KeyStore keyStore, ProtocolVersion version) {
        this.gson = gson;
        this.keyStore = keyStore;
        this.version = version;
        this.deviceId = deviceId;
    }

    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in,
            List<Object> out) {
        if (in.readableBytes() < 24) {
            // minimum packet size is 16 bytes header + 8 bytes suffix
            return;
        }

        // we need to take a copy first so the buffer stays intact if we exit early
        ByteBuf inCopy = in.copy();
        byte[] bytes = new byte[inCopy.readableBytes()];
        inCopy.readBytes(bytes);
        inCopy.release();

        if (log.isTraceEnabled()) {
            log.trace("{}{}: Received encoded '{}'", deviceId,
                Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""), bytesToHex(bytes));
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int prefix = buffer.getInt();
        int sequenceNumber = buffer.getInt();
        CommandType commandType = CommandType.fromCode(buffer.getInt());
        int payloadLength = buffer.getInt();

        //
        if (buffer.limit() < payloadLength + 16) {
            // there are less bytes than needed, exit early
            log.trace("Did not receive enough bytes from '{}', exiting early", deviceId);
            return;
        } else {
            // we have enough bytes, skip them from the input buffer and proceed processing
            in.skipBytes(payloadLength + 16);
        }

        int returnCode = buffer.getInt();

        byte[] payload;
        if ((returnCode & 0xffffff00) != 0) {
            // rewind if no return code is present
            buffer.position(buffer.position() - 4);
            payload = version == V3_4 ? new byte[payloadLength - 32] : new byte[payloadLength - 8];
        } else {
            payload = version == V3_4 ? new byte[payloadLength - 32 - 8] : new byte[payloadLength - 8 - 4];
        }

        buffer.get(payload);

        if (version == V3_4 && commandType != UDP && commandType != UDP_NEW) {
            byte[] fullMessage = new byte[buffer.position()];
            buffer.position(0);
            buffer.get(fullMessage);
            byte[] expectedHmac = new byte[32];
            buffer.get(expectedHmac);
            byte[] calculatedHmac = CryptoUtil.hmac(fullMessage, keyStore.getSessionKey());
            if (!Arrays.equals(expectedHmac, calculatedHmac)) {
                log.warn("{}{}: Checksum failed for message: calculated {}, found {}", deviceId,
                    Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""),
                    calculatedHmac != null ? bytesToHex(calculatedHmac) : "<null>", bytesToHex(expectedHmac));
                return;
            }
        } else {
            int crc = buffer.getInt();
            // header + payload without suffix and checksum
            int calculatedCrc = CryptoUtil.calculateChecksum(bytes, 0, 16 + payloadLength - 8);
            if (calculatedCrc != crc) {
                log.warn("{}{}: Checksum failed for message: calculated {}, found {}", deviceId,
                        Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""), calculatedCrc, crc);
                return;
            }
        }

        int suffix = buffer.getInt();
        if (prefix != 0x000055aa || suffix != 0x0000aa55) {
            log.warn("{}{}: Decoding failed: Prefix or suffix invalid.", deviceId,
                    Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""));
            return;
        }

        if (Arrays.equals(Arrays.copyOfRange(payload, 0, version.getBytes().length), version.getBytes())) {
            if (version == V3_3) {
                // Remove 3.3 header
                payload = Arrays.copyOfRange(payload, 15, payload.length);
            } else {
                payload = Base64.getDecoder().decode(Arrays.copyOfRange(payload, 19, payload.length));
            }
        }

        MessageWrapper<?> m;
        if (commandType == UDP) {
            // UDP is unencrypted
            m = new MessageWrapper<>(commandType,
                    Objects.requireNonNull(gson.fromJson(new String(payload), DiscoveryMessage.class)));
        } else {
            byte[] decodedMessage = version == V3_4 ? CryptoUtil.decryptAesEcb(payload, keyStore.getSessionKey(), true)
                    : CryptoUtil.decryptAesEcb(payload, keyStore.getDeviceKey(), false);
            if (decodedMessage == null) {
                return;
            }
            if (Arrays.equals(Arrays.copyOfRange(decodedMessage, 0, version.getBytes().length), version.getBytes())) {
                if (version == V3_4) {
                    // Remove 3.4 header
                    decodedMessage = Arrays.copyOfRange(decodedMessage, 15, decodedMessage.length);
                }
            }
            if (log.isTraceEnabled()) {
                log.trace("{}{}: Decoded raw payload: {}", deviceId,
                    Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""),
                    bytesToHex(decodedMessage));
            }

            try {
                String decodedString = new String(decodedMessage).trim();
                if (commandType == DP_QUERY && "json obj data unvalid".equals(decodedString)) {
                    // "json obj data unvalid" would also result in a JSONSyntaxException but is a known error when
                    // DP_QUERY is not supported by the device. Using a CONTROL message with null values is a known
                    // workaround, cf. https://github.com/codetheweb/tuyapi/blob/master/index.js#L156
                    log.info("{}{}: DP_QUERY not supported. Trying to request with CONTROL.", deviceId,
                            Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""));
                    m = new MessageWrapper<>(DP_QUERY_NOT_SUPPORTED, Map.of());
                } else if (commandType == STATUS || commandType == DP_QUERY) {
                    m = new MessageWrapper<>(commandType,
                            Objects.requireNonNull(gson.fromJson(decodedString, TcpStatusPayload.class)));
                } else if (commandType == UDP_NEW || commandType == BROADCAST_LPV34) {
                    m = new MessageWrapper<>(commandType,
                            Objects.requireNonNull(gson.fromJson(decodedString, DiscoveryMessage.class)));
                } else {
                    m = new MessageWrapper<>(commandType, decodedMessage);
                }
            } catch (JsonSyntaxException e) {
                log.warn("{}{} failed to parse JSON: {}", deviceId,
                        Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""), e.getMessage());
                return;
            }
        }

        log.debug("{}{}: Received {}", deviceId, Objects.requireNonNullElse(ctx.channel().remoteAddress(), ""), m);
        out.add(m);
    }
}
