package org.touchhome.bundle.api.hardware.other;

import io.swagger.annotations.ApiParam;
import org.touchhome.bundle.api.hardware.api.HardwareQuery;
import org.touchhome.bundle.api.hardware.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface PostgreSQLHardwareRepository {

    @HardwareQuery(echo = "Install postgresql", value = "$PM install -y postgresql", printOutput = true, maxSecondsTimeout = 300)
    void installPostgreSQL();

    @HardwareQuery("psql --version")
    String getPostgreSQLVersion();

    @HardwareQuery(value = {"service", "postgresql", "status"}, printOutput = true)
    boolean isPostgreSQLRunning();

    @HardwareQuery(echo = "Alter postgres password", value = "su - postgres -c \"psql -c \\\"ALTER USER postgres PASSWORD 'postgres';\\\"\"")
    void changePostgresPassword(@ApiParam("pwd") String pwd);

    @HardwareQuery(echo = "Start postgresql", value = "service postgresql start", printOutput = true)
    void startPostgreSQLService();

}









