package org.touchhome.bundle.api.hardware.other;

import io.swagger.annotations.ApiParam;
import org.touchhome.bundle.api.hardware.api.HardwareQueries;
import org.touchhome.bundle.api.hardware.api.HardwareQuery;
import org.touchhome.bundle.api.hardware.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface PostgreSQLHardwareRepository {

    @HardwareQueries({
            @HardwareQuery("sudo $PM update"),
            @HardwareQuery(value = "sudo $PM install -y postgresql", printOutput = true, maxSecondsTimeout = 300),
    })
    void installPostgreSQL();

    @HardwareQuery("psql --version")
    String getPostgreSQLVersion();

    @HardwareQuery("sudo -u postgres psql -c \"ALTER USER postgres PASSWORD ':pwd';\"")
    String changePostgresPassword(@ApiParam("pwd") String pwd);
}









