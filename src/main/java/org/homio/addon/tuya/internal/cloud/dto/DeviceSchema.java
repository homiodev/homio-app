package org.homio.addon.tuya.internal.cloud.dto;

import java.util.List;
import lombok.ToString;

@ToString
public class DeviceSchema {

    public String category = "";
    public List<Description> functions = List.of();
    public List<Description> status = List.of();

    @ToString
    public static class Description {

        public String code = "";
        public int dp_id = 0;
        public String type = "";
        public String values = "";
    }
}
