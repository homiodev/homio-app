package org.touchhome.bundle.api.hardware.other;

import io.swagger.annotations.ApiParam;
import org.touchhome.bundle.api.hardware.api.HardwareQueries;
import org.touchhome.bundle.api.hardware.api.HardwareQuery;
import org.touchhome.bundle.api.hardware.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface GPIOHardwareRepository {
    @HardwareQuery(value = "gpio -v", printOutput = true)
    void printWiringPiVersion();

    @HardwareQuery(value = "gpio readall", printOutput = true)
    void printWiringPiInfo();

    @HardwareQuery(value = "sudo $PM install wiringpi", printOutput = true, ignoreOnError = true)
    void installWiringPiAuto();

    @HardwareQueries(
            value = {
                    @HardwareQuery("mkdir buildWiringPi"),
                    @HardwareQuery("cp :sysDir/WiringPi-master.zip buildWiringPi/"),
                    @HardwareQuery("unzip buildWiringPi/WiringPi-master.zip -d buildWiringPi/"),
                    @HardwareQuery(value = "./build", dir = ":tomcatDir/buildWiringPi/WiringPi-master"),
                    @HardwareQuery("rm -rf buildWiringPi")
            }
    )
    void installWiringPiManually(@ApiParam("sysDir") String sysDir, @ApiParam("tomcatDir") String tomcatDir);
}









