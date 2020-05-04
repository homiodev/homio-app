package org.touchhome.bundle.googleDrive;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.repository.impl.UserRepository;

import java.io.IOException;

@Log4j2
@Component
public class GoogleDriveCloudManager {

    @Autowired
    private UserRepository userRepository;

    @Value("${googleDriveClientSecretFile:SmartHouseGDriveClientSecret.json}")
    private String googleDriveClientSecretFile;

    //SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

    @Autowired
    private GoogleDriveFileSystem googleDriveFileSystem;

    private synchronized GoogleDriveFileSystem.Folder getFolder(String folder) throws IOException, GoogleDriveFileSystem.CodeExchangeException {
        //try {
        //   GoogleDriveFileSystem.Folder userFolder = googleDriveFileSystem.getRootFolder().getChildFolder(UserRepository.DEFAULT_USER_ID, true);
        //   return userFolder.getChildFolder(folder, true);
        return null;
        /*} catch (Exception ex) {
            throw new RuntimeException(ex);
        }*/
    }

    private GoogleDriveFileSystem.Folder getUserRestFolder() throws IOException, GoogleDriveFileSystem.CodeExchangeException {
        return getFolder("rest");
    }

    private GoogleDriveFileSystem.Folder getUserVariableGroupFolder() throws IOException, GoogleDriveFileSystem.CodeExchangeException {
        return getFolder("VariableGroups");
    }

    private GoogleDriveFileSystem.Folder getApplicationFolder() throws IOException, GoogleDriveFileSystem.CodeExchangeException {
        return getFolder("Application");
    }

    /*public List<RestEntity> getCloudRests() {
        List<RestEntity> restEntities = new ArrayList<>();
        for (File file : getUserRestFolder().getAllFiles()) {
            RestEntity restEntity = new RestEntity();
            pushPropertiesFromCloud(restEntity, file);
            restEntity.setHelpID(file.getCommandIndex());
            restEntities.add(restEntity);
        }
        return restEntities;
    }*/

/*    public File getFile(CloudStoragable entity) throws IOException, GoogleDriveFileSystem.CodeExchangeException {
        return getFile(entity.getCloudFolder(), entity.getEntityID());
    }*/

   /* public File getFile(String page, String entityID) throws IOException, GoogleDriveFileSystem.CodeExchangeException {
        return getFolder(page).getAllFiles().stream().filter(file -> entityID.equals(googleDriveFileSystem.getPropertyValue(file, "entityID", null))).findAny().orElse(null);
    }
*/
   /* public List<VariableGroupEntity> getCloudVarGroups() {
        return getUserVariableGroupFolder().getAllFiles().stream().map(file -> {
            VariableGroupEntity entity = new VariableGroupEntity();
            pushPropertiesFromCloud(entity, file);
            entity.setHelpID(file.getCommandIndex());
            return entity;
        }).collect(Collectors.toList());
    }*/

   /* public synchronized void addItemToCloud(CloudStoragable entity) {
        Path tempFile = null;
        try {
            byte[] cloudFile = entity.getCloudFile();
            if (cloudFile != null) {
                tempFile = Files.createTempFile("tmp_" + System.currentTimeMillis(), ".txt");
                Files.write(tempFile, cloudFile);
               // getFolder(entity.getCloudFolder()).addOrUpdateFile(tempFile, entity.getMimeType(), entity.getTitle(), entity.getTitle(), fetchPropertiesFromObject(entity));
            } else {
            //    getFolder(entity.getCloudFolder()).addOrUpdateFile(entity.getTitle(), entity.getTitle(), fetchPropertiesFromObject(entity));
            }
        } catch (Exception ex) {
            log.error(TouchHomeUtils.getErrorMessage(ex)
                    , ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ex) {
                    log.error(TouchHomeUtils.getErrorMessage(ex), ex);
                }
            }
        }
    }*/

    /*public void updateFromCloud(CloudStoragable baseEntity) throws IOException, GoogleDriveFileSystem.CodeExchangeException {
        File file = getFile(baseEntity);
        //pushPropertiesFromCloud(baseEntity, file);
    }*/

/*    public Date getCloudAppVersion() throws IOException, GoogleDriveFileSystem.CodeExchangeException {
        Optional<File> optional = getApplicationFolder().getAllFiles().stream().filter(file -> file.getTitle().equals(Constants.SMART_HOUSE_WAR_CONSTANT)).findFirst();
        if (optional.isPresent()) {
            return new Date(optional.get().getCreatedDate().getValue());
        }
        return null;
    }*/

/*    public Path getAppFile() throws IOException, GoogleDriveFileSystem.CodeExchangeException {
        File file1 = getApplicationFolder().getAllFiles().stream().filter(file -> file.getTitle().equals(Constants.SMART_HOUSE_WAR_CONSTANT)).findFirst().get();
        InputStream inputStream = googleDriveFileSystem.getFileInputStream(file1.getId());
        Path warFile = SystemUtils.getUserHome().toPath().resolve(Constants.SMART_HOUSE_WAR_CONSTANT);
        Files.copy(inputStream, warFile, StandardCopyOption.REPLACE_EXISTING);
        return warFile;
    }*/

   /* private List<Property> fetchPropertiesFromObject(CloudStoragable entity) {
        List<Property> properties = new ArrayList<>();
        for (Field field : FieldUtils.getFieldsListWithAnnotation(entity.getClass(), CloudProperty.class)) {
            try {
                Object o = FieldUtils.readField(field, entity, true);
                if (o != null) {
                    properties.add(new Property().setKey(field.getName()).setValue(o.toString()));
                }
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }
        return properties;
    }

    private void pushPropertiesFromCloud(CloudStoragable entity, File file) {
        for (Field field : FieldUtils.getFieldsListWithAnnotation(entity.getClass(), CloudProperty.class)) {
            try {
                FieldUtils.writeField(field, entity, googleDriveFileSystem.getPropertyValue(field.getType(), file, field.getName()), true);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
*/
    /*public CloudItemStatus getStatus(CloudStoragable entity) {
        File file;
        try {
            file = getFile(entity);
        } catch (Exception ex) {
            return CloudItemStatus.NOT_LOGGED_IN;
        }
        if (file == null) {
            return CloudItemStatus.NOT_EXISTS;
        }
        int currentVersion = entity.getVersion();
        int cloudVersion = googleDriveFileSystem.getPropertyValueInteger(file, "version", -1);
        if (currentVersion > cloudVersion) {
            return CloudItemStatus.CLOUD_OUT_OF_DATE;
        }
        if (currentVersion < cloudVersion) {
            return CloudItemStatus.DB_OUT_OF_DATE;
        }
        return CloudItemStatus.UP_TO_DATE;
    }*/

    /*public List<CloudStoragable> getCloudItems(String page) throws IllegalAccessException, InstantiationException, IOException, GoogleDriveFileSystem.CodeExchangeException {
        Class<? extends BaseEntity> byPage = internalManager.getEntityManager().getClassByPage(page);
        if (byPage == null) {
            return Collections.emptyList();
        }
        List<File> allFiles = getFolder(page).getAllFiles();
        AbstractRepository<? extends BaseEntity> repository = internalManager.getEntityManager().getRepositoryByClass(byPage);
        List<CloudStoragable> list = new ArrayList<>();
        for (File file : allFiles) {
            CloudStoragable cloudStoragable = (CloudStoragable) byPage.newInstance();
            //pushPropertiesFromCloud(cloudStoragable, file);
            if (!repository.isExistsByEntityID(cloudStoragable.getEntityID())) {
                list.add(cloudStoragable);
            }
        }
        return list;
    }*/

/*    public void uploadAppToCloud(String path) throws IOException, GoogleDriveFileSystem.CodeExchangeException {
        getApplicationFolder().addOrUpdateFile(Paths.get(path), MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE, Constants.SMART_HOUSE_WAR_CONSTANT, "SmartHouse", new ArrayList<>());
    }*/
}












