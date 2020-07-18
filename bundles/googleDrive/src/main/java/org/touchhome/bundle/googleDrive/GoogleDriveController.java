package org.touchhome.bundle.googleDrive;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Log4j2
@RestController
@RequestMapping("/rest/googleDrive")
@RequiredArgsConstructor
public class GoogleDriveController {

    private final GoogleDriveCloudManager googleDriveCloudManager;
    private final GoogleDriveFileSystem googleDriveFileSystem;

    @GetMapping("content")
    public String getCloudFileContent(@RequestParam("fileID") String fileID) {
        return googleDriveFileSystem.getCloudFileContent(fileID);
    }

    @GetMapping("loginToCloud")
    public String loginToCloud(@RequestParam(value = "code", required = false, defaultValue = "") String code, HttpServletResponse response) throws IOException {
        try {
            googleDriveFileSystem.login(StringUtils.trimToNull(code));
        } catch (GoogleDriveFileSystem.CodeExchangeException e) {
            return e.getAuthorizationUrl();
        }
        response.sendRedirect("/");
        return null;
    }

    @GetMapping("isLoggedInGoogleOauth2")
    public Boolean isLoggedInGoogleOauth2() {
        return googleDriveFileSystem.isLoggedInGoogleOauth2();
    }

    @GetMapping("googleDriveCloudResponse")
    public void googleDriveCloudResponse(@RequestParam("code") String code, HttpServletResponse response) throws GoogleDriveFileSystem.CodeExchangeException {
        try {
            googleDriveFileSystem.login(code);
        } catch (GoogleDriveFileSystem.CodeExchangeException ex) {
            log.error(TouchHomeUtils.getErrorMessage(ex), ex);
            throw ex;
        }
        try {
            response.sendRedirect("/");
        } catch (IOException ex) {
            log.error(TouchHomeUtils.getErrorMessage(ex), ex);
        }
    }

    /*@RequestMapping(value = "/getCloudRests", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RestEntity> getCloudRests() {
        try {
            List<RestEntity> cloudRests = googleDriveCloudManager.getCloudRests();
            cloudRests.forEach(cr -> {
                RestEntity entity = restsRepository.getByURL(cr.getUrl());
                cr.setExistsInDB(entity != null && Objects.equals(entity.getVersion(), cr.getVersion()));
            });
            return cloudRests;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }*/

    /*@RequestMapping(value = "/addRestToCloud", method = RequestMethod.POST)
    public void addRestToCloud(@RequestBody RestEntity restEntity) {
        //googleDriveCloudManager.addItemToCloud(restEntity);
    }*/

   /* @Deprecated
    @RequestMapping(value = "/updateRestFromCloud", method = RequestMethod.POST)
    public void updateRestFromCloud(@RequestBody RestEntity cloudRestEntity) {
        RestEntity entity = restsRepository.getByURL(cloudRestEntity.getUrl());
        entity.setEntityID(cloudRestEntity.getEntityID());
        entity.setName(cloudRestEntity.getName());
        entity.setVersion(cloudRestEntity.getVersion());
        //       entity.setRequiredParams(cloudRestEntity.getRequiredParams());
        ScriptEntity script = entity.getScriptEntity();
        script.setJavaScript(googleDriveFileSystem.getCloudFileContent(cloudRestEntity.getHelpID()));
        script.setCreationTime(new Date());
        restsRepository.save(entity);
    }*/

    /*@RequestMapping(value = "/updateVariableGroupFromCloud", method = RequestMethod.POST)
    public void updateVariableGroupFromCloud(@RequestBody VariableGroupEntity cloudVariableGroupEntity) {
        VariableGroupEntity entity = variableGroupRepository.getByName(cloudVariableGroupEntity.getName());
        entity.setEntityID(cloudVariableGroupEntity.getEntityID());
        entity.setName(cloudVariableGroupEntity.getName());
        entity.setVersion(cloudVariableGroupEntity.getVersion());
        File imageFile = googleDriveFileSystem.getFile(cloudVariableGroupEntity.getHelpID());
        String imageName = googleDriveFileSystem.getPropertyValue(imageFile, "ImageName", cloudVariableGroupEntity.getName());

        InputStream inputStream = googleDriveFileSystem.getFileInputStream(cloudVariableGroupEntity.getHelpID());
        try {
            ImageEntity imageEntity = imageManager.upload(imageName, ImageIO.read(inputStream));
            entity.setImageID(imageEntity.getEntityID());
        } catch (IOException ex) {
            throw new RuntimeException("Can not upload image", ex);
        }
        variableGroupRepository.save(entity);
    }*/

    /*@RequestMapping(value = "/getCloudVarGroups", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<VariableGroupEntity> getCloudVarGroups() {
        try {
            List<VariableGroupEntity> cloudRests = googleDriveCloudManager.getCloudVarGroups();
            cloudRests.forEach(cr -> {
                VariableGroupEntity entity = variableGroupRepository.getByName(cr.getName());
                cr.setExistsInDB(entity != null && Objects.equals(entity.getVersion(), cr.getVersion()));
            });
            return cloudRests;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }*/

   /* @RequestMapping(value = "/getCloudItemStatus", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public CloudItemStatus getCloudItemStatus(@RequestParam(value = "entityID") String entityID) {
        BaseEntity baseEntity = manager.getEntity(entityID);
        return baseEntity instanceof CloudStoragable ? googleDriveCloudManager.getStatus((CloudStoragable) baseEntity) : CloudItemStatus.NOT_SUPPORTED;
    }

    @RequestMapping(value = "/addToCloud", method = RequestMethod.GET)
    public String addToCloud(@RequestParam(value = "entityID") String entityID) {
        BaseEntity baseEntity = manager.getEntityWithFetchLazy(entityID);
        googleDriveCloudManager.addItemToCloud((CloudStoragable) baseEntity);
        return "ReloadPage";
    }

    @RequestMapping(value = "/updateToCloud", method = RequestMethod.GET)
    public String updateToCloud(@RequestParam(value = "entityID") String entityID) {
        BaseEntity baseEntity = manager.getEntityWithFetchLazy(entityID);
        googleDriveCloudManager.addItemToCloud((CloudStoragable) baseEntity);
        return "ReloadPage";
    }

    @RequestMapping(value = "/createFromCloud", method = RequestMethod.GET)
    public void createFromCloud(@RequestParam(value = "entityID") String entityID,
                                @RequestParam(value = "page") String page) throws IOException, IllegalAccessException, InstantiationException, GoogleDriveFileSystem.CodeExchangeException {
        Class<? extends BaseEntity> byPage = entityManager.getClassByPage(page);
        AbstractRepository entityRepositoryByClass = entityManager.getRepositoryByClass(byPage);

        CloudStoragable entity = (CloudStoragable) byPage.newInstance();
        entity.setEntityID(entityID);
        googleDriveCloudManager.updateFromCloud(entity);
        manager.save((BaseEntity) entity);
    }

    @RequestMapping(value = "/updateFromCloud", method = RequestMethod.GET)
    public String updateFromCloud(@RequestParam(value = "entityID") String entityID) throws IOException, GoogleDriveFileSystem.CodeExchangeException {
        BaseEntity baseEntity = manager.getEntityWithFetchLazy(entityID);
        googleDriveCloudManager.updateFromCloud((CloudStoragable) baseEntity);

        manager.save(baseEntity);
        return "ReloadPage";
    }

    @RequestMapping(value = "/getCloudItems", method = RequestMethod.GET)
    public List<CloudStoragable> getCloudItems(@RequestParam(value = "page") String page) throws IOException, InstantiationException, IllegalAccessException, GoogleDriveFileSystem.CodeExchangeException {
        return googleDriveCloudManager.getCloudItems(page);
    }
    @RequestMapping(value = "/isItemCloudStoragable", method = RequestMethod.GET)
    public boolean isItemCloudStoragable(@RequestParam(value = "type") String type) {
        Class<?> entityClassByType = getEntityClassByType(type);
        return entityClassByType != null && CloudStoragable.class.isAssignableFrom(entityClassByType);
    }
*/
}
