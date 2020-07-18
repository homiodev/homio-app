package org.touchhome.bundle.api.hardware.other;

import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.touchhome.bundle.api.hquery.api.ListParse;

@Getter
@ToString
public class HardwareOs {

    @ListParse.LineParse("ID=(.*)")
    private String id;

    @ListParse.LineParse("ID_LIKE=(.*)")
    private String idLike;

    @ListParse.LineParse("NAME=(.*)")
    private String name;

    @ListParse.LineParse("VERSION=(.*)")
    private String version;

    public String getPackageManager() {
        switch (StringUtils.defaultString(idLike, id)) {
            case "debian":
                return "apt-get";
            case "rhel fedora":
                return "yum";
        }
        throw new IllegalStateException("Unable to find package manager");
    }
}
