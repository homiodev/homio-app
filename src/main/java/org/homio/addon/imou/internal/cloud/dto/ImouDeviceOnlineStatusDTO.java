package org.homio.addon.imou.internal.cloud.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.homio.api.model.Status;

@Getter
@Setter
@ToString
public class ImouDeviceOnlineStatusDTO {

    private List<Channel> channels;
    private String deviceId;
    private int onLine;

    public Status getStatus() {
        return switch (onLine) {
            case 0 -> Status.OFFLINE;
            case 1 -> Status.ONLINE;
            case 3 -> Status.UPDATING;
            case 4 -> Status.SLEEPING;
            default -> Status.UNKNOWN;
        };
    }

    @Getter
    @Setter
    @ToString
    public static class Channel {
        private String channelId;
        private int onLine;
    }
}
