package org.touchhome.bundle.api.hardware.other;

import io.swagger.annotations.ApiParam;
import org.touchhome.bundle.api.hardware.api.HardwareQueries;
import org.touchhome.bundle.api.hardware.api.HardwareQuery;
import org.touchhome.bundle.api.hardware.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface StartupHardwareRepository {

    @HardwareQuery("sudo sed -i 's/\"exit 0\"/\"Hi, my mate\"/' /etc/rc.local")
    @HardwareQuery("grep -xF ':cmd' /etc/rc.local && echo '1' || sudo sed -i '/exit 0/i :cmd' /etc/rc.local")
    String addStartupCommand(@ApiParam("cmd") String cmd);

    @HardwareQuery("sudo ./update.sh :version")
    String updateApp(@ApiParam("version") String version);
}
