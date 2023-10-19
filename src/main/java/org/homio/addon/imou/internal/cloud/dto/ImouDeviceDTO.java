package org.homio.addon.imou.internal.cloud.dto;

import java.util.List;
import lombok.Getter;
import lombok.ToString;

@ToString
public class ImouDeviceDTO {

    public int channelNum;
    public String baseline;
    public String deviceId;
    public String version;
    public List<ImouChannel> channels;
    public int encryptMode;
    public String appId;
    public String deviceCatalog;
    public String name;
    public boolean tlsEnable;
    public String deviceModel;
    public String ability;
    public boolean canBeUpgrade;
    public String brand;
    public int platForm;
    public int status;

    @Getter
    public static class ImouChannel {

        public int csStatus;
        public int alarmStatus;
        public String channelName;
        public boolean channelOnline;
        public List<ImouResolution> resolutions;
        public String channelAbility;
        public int channelId;
        public String channelPicUrl;
        public boolean shareStatus;

        public static class ImouResolution {

            public int streamType;
            public String name;
            public int imageSize;
        }
    }
}
