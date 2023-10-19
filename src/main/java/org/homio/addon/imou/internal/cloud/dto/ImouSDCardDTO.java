package org.homio.addon.imou.internal.cloud.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ImouSDCardDTO {

    private String status;

    @Getter
    @Setter
    public static class ImouSDCardStatusDTO {

        private long totalBytes;
        private long usedBytes;

        public String toString() {
            return (int) ((double) usedBytes / (1024 * 1024)) + "/" + (int) ((double) totalBytes / (1024 * 1024));
        }
    }
}
