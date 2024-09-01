package org.homio.app.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.app.json.BgpProcessResponse;
import org.homio.app.manager.common.ContextImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static org.homio.api.util.Constants.ROLE_ADMIN_AUTHORIZE;

@Log4j2
@RestController
@RequestMapping("/rest/bgp")
@RequiredArgsConstructor
public class BackgroundProcessController {

    private final ContextImpl context;

    @DeleteMapping("/{name}")
    @PreAuthorize(ROLE_ADMIN_AUTHORIZE)
    public void cancelProcess(@PathVariable("name") String name) {
        context.bgp().cancelThread(name);
        context.ui().progress().cancel(name);
    }

    @GetMapping
    public BgpProcessResponse getProcesses() {
        return context.bgp().getProcesses();
    }

    /* @GetMapping("/dynamic/stop/{url}")
    public void stopScriptByName(@PathVariable String url) {
        ScriptEntity scriptEntity = scriptRepository.getByURL(url);
        if (scriptEntity != null) {
            log.info("Stop script: " + scriptEntity.getEntityID());
            scriptManager.stopThread(scriptEntity);
        }
    }*/

    /* @GetMapping("/dynamic/{url}")
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

            BaseEntity baseEntity = context.db().get(entityID);
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
