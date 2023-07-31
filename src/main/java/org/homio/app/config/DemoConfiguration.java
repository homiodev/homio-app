package org.homio.app.config;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.Iterator;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.entity.BaseEntity;
import org.homio.app.model.entity.ScriptEntity;
import org.homio.app.model.entity.WorkspaceEntity;
import org.homio.app.model.entity.widget.WidgetBaseEntity;
import org.homio.app.model.entity.widget.WidgetTabEntity;
import org.homio.app.repository.WorkspaceVariableRepository;
import org.homio.app.repository.device.WorkspaceRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;

@Log4j2
@Profile("demo")
@Configuration
public class DemoConfiguration {

    @Autowired
    private EntityContext entityContext;

    @Scheduled(fixedDelay = 600000)
    private void clearOutdatedData() {
        try {
            log.info("demo profile: clear");
            removeItems(WidgetBaseEntity.class, ScriptEntity.class);

            clearWidgets();
            clearWorkspace();
        } catch (Exception ex) {
            log.error("Error while clean demo data", ex);
        }
    }

    private void removeItems(Class<? extends BaseEntity>... classes) {
        for (Class<? extends BaseEntity> aClass : classes) {
            for (BaseEntity baseEntity : entityContext.findAll(aClass)) {
                if (quiteOld(baseEntity)) {
                    entityContext.delete(baseEntity);
                }
            }
        }
    }

    private void clearWidgets() {
        for (WidgetTabEntity tab : entityContext.findAll(WidgetTabEntity.class)) {
            if (!WidgetTabEntity.GENERAL_WIDGET_TAB_NAME.equals(tab.getEntityID()) && tab.getWidgetBaseEntities().isEmpty()) {
                entityContext.delete(tab);
            }
        }
    }

    private boolean quiteOld(BaseEntity baseEntity) {
        return MILLISECONDS.toMinutes(System.currentTimeMillis() - baseEntity.getEntityUpdated().getTime()) > 10;
    }

    private void clearWorkspace() {
        entityContext.getBean(WorkspaceVariableRepository.class).deleteAll();

        for (WorkspaceEntity tab : entityContext.findAll(WorkspaceEntity.class)) {
            if (!tab.getName().equals(WorkspaceRepository.GENERAL_WORKSPACE_TAB_NAME) && quiteOld(tab)) {
                // first clean workspace
                entityContext.save(tab.setContent(""));
                // then drop it
                entityContext.delete(tab);
            }
        }
    }

    private void removeOutdatedVariables(JSONObject list, String repositoryPrefix) {
        if (list != null) {
            for (Iterator<String> iterator = list.keys(); iterator.hasNext(); ) {
                String id = iterator.next();
                BaseEntity entity = entityContext.getEntity(repositoryPrefix + id);
                if (entity != null &&
                    MILLISECONDS.toMinutes(System.currentTimeMillis() - entity.getEntityUpdated().getTime()) > 10) {
                    entityContext.delete(entity);
                    iterator.remove();
                }
            }
        }
    }
}
