package org.homio.app;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.homio.app.setting.console.lines.ConsoleDebugLevelSetting;
import org.homio.bundle.api.EntityContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles(profiles = {"test"})
public class StartupTest {

    @Autowired private EntityContext entityContext;

    @Test
    public void testStartup() {
        assertNotNull(entityContext);
        assertTrue(entityContext.setting().getValue(ConsoleDebugLevelSetting.class));
    }
}
