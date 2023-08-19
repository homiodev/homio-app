package org.homio.addon.camera.rtsp.message.sdp;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SDP: Session Description Protocol
 */
public final class SdpMessage {

    private static final String CRLF = "\r\n";
    @Getter
    private final String sessionName;
    private final List<Media> mediaDescriptions = new ArrayList<>();
    private final Map<String, String> attributes = new HashMap<>();
    @Getter
    private final String control;
    private String fineString;

    /**
     * Описание сессии
     *
     * @param sessionName имя
     * @param control     Control URL Attribute
     */
    public SdpMessage(String sessionName, String control) {
        this.sessionName = sessionName;
        this.control = control;
    }

    public void addMedia(Media media) {
        mediaDescriptions.add(media);
    }

    @Override
    public String toString() {
        if (fineString == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("v=0\r\n");
            sb.append("o=RTSP 50539017935697 1 IN IP4 0.0.0.0").append(CRLF);
            sb.append("s=").append(sessionName).append(CRLF);

            attributes.forEach((k, v) -> sb.append("a=").append(k).append(':').append(v).append(CRLF));

            for (Media media : mediaDescriptions) {
                sb.append("m=").append(media.getMediaType()).append(" ")
                        .append(media.getPort()).append(" ")
                        .append(media.getTransport()).append(" ")
                        .append(media.getFmtList()).append(CRLF);

                Fmtp fmtp = media.getFmtp();
                RtpMap rtpmap = media.getRtpMap();
                if (fmtp != null) {
                    sb.append("a=fmtp:").append(fmtp.getFormat()).append(" ")
                            .append(fmtp.getParameters().toString()).append(CRLF);
                }

                if (rtpmap != null) {
                    sb.append("a=rtpmap:").append(rtpmap.getPayloadType()).append(" ")
                            .append(rtpmap.getEncodingName()).append('/').append(rtpmap.getClockRate()).append(CRLF);
                }
                sb.append("a=control:").append(media.getControl()).append(CRLF);
            }

            fineString = sb.toString();
        }
        return fineString;
    }
}
