package org.homio.addon.tuya.internal.cloud.dto;

import lombok.ToString;

/**
 * Encapsulates device status data
 */
@ToString
public class StatusInfo {
    public String code = "";
    public String value = "";
    public String t = "";
}
