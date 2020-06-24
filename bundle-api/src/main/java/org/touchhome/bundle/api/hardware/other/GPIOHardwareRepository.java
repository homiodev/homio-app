package org.touchhome.bundle.api.hardware.other;

import org.touchhome.bundle.api.hquery.api.HQueryParam;
import org.touchhome.bundle.api.hquery.api.HardwareQuery;
import org.touchhome.bundle.api.hquery.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface GPIOHardwareRepository {
    @HardwareQuery(echo = "Printing wiring PI info", value = "gpio readall", printOutput = true)
    boolean printWiringPiInfo();

    @HardwareQuery(echo = "Install GPIO", value = "$PM install wiringpi", printOutput = true, ignoreOnError = true)
    void installWiringPiAuto();

    @HardwareQuery("mkdir buildWiringPi")
    @HardwareQuery("cp :sysDir/WiringPi-master.zip buildWiringPi/")
    @HardwareQuery("unzip buildWiringPi/WiringPi-master.zip -d buildWiringPi/")
    @HardwareQuery(value = "./build", dir = ":tomcatDir/buildWiringPi/WiringPi-master")
    @HardwareQuery("rm -rf buildWiringPi")
    void installWiringPiManually(@HQueryParam("sysDir") String sysDir, @HQueryParam("tomcatDir") String tomcatDir);
}
