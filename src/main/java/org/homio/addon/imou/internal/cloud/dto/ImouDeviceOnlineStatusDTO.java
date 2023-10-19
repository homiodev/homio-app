package org.homio.addon.imou.internal.cloud.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ImouDeviceOnlineStatusDTO {

    private List<Channel> channels;
    private String deviceId;
    private Status onLine;

    @Getter
    @Setter
    @ToString
    public static class Channel {
        private String channelId;
        private Status onLine;
    }

    public enum Status {
        Offline, Online, Unknown, Upgrading, Sleeping
    }
}
