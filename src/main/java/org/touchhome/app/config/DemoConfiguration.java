package org.touchhome.app.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.app.model.workspace.WorkspaceBroadcastEntity;
import org.touchhome.app.repository.device.WorkspaceRepository;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.PlaceEntity;
import org.touchhome.bundle.api.entity.widget.WidgetBaseEntity;
import org.touchhome.bundle.api.entity.widget.WidgetTabEntity;
import org.touchhome.bundle.api.entity.workspace.WorkspaceShareVariableEntity;
import org.touchhome.bundle.api.entity.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.entity.workspace.backup.WorkspaceBackupGroupEntity;
import org.touchhome.bundle.api.entity.workspace.bool.WorkspaceBooleanGroupEntity;
import org.touchhome.bundle.api.entity.workspace.var.WorkspaceVariableGroupEntity;
import org.touchhome.bundle.api.workspace.WorkspaceEntity;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static org.touchhome.bundle.api.util.TouchHomeUtils.OBJECT_MAPPER;

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
            removeItems(WidgetBaseEntity.class, ScriptEntity.class, PlaceEntity.class);

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
            if (!WidgetTabEntity.GENERAL_WIDGET_TAB_NAME.equals(tab.getName()) && tab.getWidgetBaseEntities().isEmpty()) {
                entityContext.delete(tab);
            }
        }
    }

    private boolean quiteOld(BaseEntity baseEntity) {
        return TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - baseEntity.getCreationTime().getTime()) > 10;
    }

    private void clearWorkspace() throws JsonProcessingException {
        WorkspaceShareVariableEntity shareVariableEntity = entityContext.getEntity(WorkspaceShareVariableEntity.PREFIX + WorkspaceShareVariableEntity.NAME);
        JSONObject target = new JSONObject(StringUtils.defaultIfEmpty(shareVariableEntity.getContent(), "{}"));
        removeOutdatedVariables(target.optJSONObject("variables"), WorkspaceStandaloneVariableEntity.PREFIX);
        removeOutdatedVariables(target.optJSONObject("broadcasts"), WorkspaceBroadcastEntity.PREFIX);
        removeOutdatedVariables(target.optJSONObject("backup_lists"), WorkspaceBackupGroupEntity.PREFIX);
        removeOutdatedVariables(target.optJSONObject("bool_variables"), WorkspaceBooleanGroupEntity.PREFIX);
        removeOutdatedVariables(target.optJSONObject("group_variables"), WorkspaceVariableGroupEntity.PREFIX);
        entityContext.save(shareVariableEntity.setContent(OBJECT_MAPPER.writeValueAsString(target)));

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
                if (entity != null && TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - entity.getCreationTime().getTime()) > 10) {
                    entityContext.delete(entity);
                    iterator.remove();
                }
            }
        }
    }
}
