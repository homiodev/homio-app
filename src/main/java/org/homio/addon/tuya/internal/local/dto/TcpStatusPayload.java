package org.homio.addon.tuya.internal.local.dto;

import lombok.ToString;

import java.util.Map;

/**
 * Encapsulates the payload of a TCP status message
 */
@ToString
public class TcpStatusPayload {
    public int protocol = -1;
    public String devId = "";
    public String gwId = "";
    public String uid = "";
    public String cid = "";
    public long t = 0;
    public Map<Integer, Object> dps = Map.of();
    public Data data = new Data();


    @ToString
    public static class Data {

        public Map<Integer, Object> dps = Map.of();
    }
}
