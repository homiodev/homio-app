package org.touchhome.app.rest;

import static org.touchhome.bundle.api.util.Constants.ADMIN_ROLE;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.json.BgpProcessResponse;
import org.touchhome.app.manager.common.EntityContextImpl;

@Log4j2
@RestController
@RequestMapping("/rest/bgp")
@RequiredArgsConstructor
public class BackgroundProcessController {

    private final EntityContextImpl entityContext;

    @DeleteMapping("/{name}")
    @Secured(ADMIN_ROLE)
    public void cancelProcess(@PathVariable("name") String name) {
        entityContext.bgp().cancelThread(name);
    }

    @GetMapping
    public BgpProcessResponse getProcesses() {
        return entityContext.bgp().getProcesses();
    }

    /* @GetMapping("/dynamic/stop/{url}")
    @Secured(ADMIN_ROLE)
    public void stopScriptByName(@PathVariable String url) {
        ScriptEntity scriptEntity = scriptRepository.getByURL(url);
        if (scriptEntity != null) {
            log.info("Stop script: " + scriptEntity.getEntityID());
            scriptManager.stopThread(scriptEntity);
        }
    }*/

    /* @GetMapping("/dynamic/{url}")
    @Secured(ADMIN_ROLE)
    public void dynamicCall(@PathVariable String url, @RequestParam(value = "json", required = false) String json) throws
    Exception {
        ScriptEntity scriptEntity = scriptRepository.getByURL(url);
        if (scriptEntity == null) {
            throw new ServerException("Dynamic URL: '" + url + "' not exists.");
        }
        if (StringUtils.isEmpty(scriptEntity.getJavaScript())) {
            throw new ServerException("Script not valid for dynamic URL: '" + url + "'.");
        }
        if (StringUtils.isEmpty(scriptEntity.getJavaScript())) {
            throw new ServerException("Script not valid for dynamic URL: '" + url + "'.");
        }
        scriptManager.startThread(scriptEntity, json, true, null, false);
    } */

    /* @GetMapping("/progress")
    public List<BackgroundProcessStatusJSON> getProgressValue(@RequestParam("values") String values) {
        List<BackgroundProcessStatusJSON> backgroundProcessStatusJSONList = new ArrayList<>();
        for (String entityIDAndKey : values.split(";")) {

            int i = entityIDAndKey.lastIndexOf(":");
            String entityID = entityIDAndKey.substring(0, i);
            String scriptDescriptor = entityIDAndKey.substring(i + 1);

            BaseEntity baseEntity = entityContext.getEntity(entityID);
            if (baseEntity instanceof HasBackgroundProcesses) {
                ScriptEntity scriptEntity = ((HasBackgroundProcesses) baseEntity).getBackgroundProcessScript(scriptDescriptor);
                if (scriptEntity != null) {
                    String backgroundProcessServiceID = scriptEntity.getBackgroundProcessServiceID();


                    BackgroundProcessService bgp = backgroundProcessManager.getBackgroundProcessByDescriptorID
                    (backgroundProcessServiceID);
                    BackgroundProcessStatusJSON backgroundProcessStatusJSON = new BackgroundProcessStatusJSON();
                    backgroundProcessStatusJSON.setEntityID(entityID);
                    backgroundProcessStatusJSON.setBackgroundProcessDescriptor(scriptDescriptor);
                    BackgroundProcessStatus scriptStatus = bgp == null ? scriptEntity.getBackgroundProcessStatus() : bgp
                    .getStatus();
                    backgroundProcessStatusJSON.setStatus(scriptStatus);
                    if (scriptStatus == BackgroundProcessStatus.FAILED) {
                        backgroundProcessStatusJSON.setErrorMessage(bgp == null ? scriptEntity.getError() : bgp.getErrorMessage
                        ());
                    }
                    backgroundProcessStatusJSON.setProgress(backgroundProcessManager.isRunning(bgp) ?
                            backgroundProcessManager.getTimeInPercentageToNextSchedule(bgp) : 0);
                    backgroundProcessStatusJSONList.add(backgroundProcessStatusJSON);
                }
            }
        }
        return backgroundProcessStatusJSONList;
    } */

    /* private void buildBackgroundProcessStatus(String entityID, List<BackgroundProcessStatusJSON> backgroundProcessStatuses,
    BaseEntity item, Class<? extends AbstractJSBackgroundProcessService> aClass) {
        try {
            AbstractJSBackgroundProcessService backgroundProcessService = aClass.getConstructor(item.getClass(),
            InternalManager.class).newInstance(item, context);
            BackgroundProcessStatusJSON backgroundProcessStatus = new BackgroundProcessStatusJSON();
            backgroundProcessStatus.setEntityID(entityID);
            backgroundProcessStatus.setBackgroundProcessDescriptor(backgroundProcessService.getClass().getSimpleName());
            if (!backgroundProcessService.canWork()) {
                backgroundProcessStatus.setErrorMessage(backgroundProcessService.whyCannotWork());
                backgroundProcessStatus.setStatus(BackgroundProcessStatusJSON.FAILED);
                backgroundProcessStatuses.add(backgroundProcessStatus);
            } else if (backgroundProcessManager.isRunning(backgroundProcessService.getCommandIndex())) {
                backgroundProcessStatus.setStatus(BackgroundProcessStatusJSON.RUNNING);
                backgroundProcessStatus.setErrorMessage(backgroundProcessStatus.getBackgroundProcessDescriptor() + " working");
                backgroundProcessStatuses.add(backgroundProcessStatus);
            } else if (backgroundProcessService.shouldStartNow()) {
                LOG.info("Somewhere bug. Restart bgp: " + backgroundProcessService.getServiceDescription());
                backgroundProcessManager.scheduleAtFixedRate(backgroundProcessService);
            }
        } catch (Exception ex) {
            throw new ServerException(ex);
        }
    } */
}
