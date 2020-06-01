package org.touchhome.bundle.googleDrive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Userinfoplus;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.BundleContext;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.repository.impl.UserRepository;

import javax.management.ObjectName;
import javax.management.Query;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.*;

import static org.touchhome.bundle.api.model.UserEntity.ADMIN_USER;

@Component
@RequiredArgsConstructor
public class GoogleDriveFileSystem implements BundleContext {
    public static final String MIME_TYPE_AUDIO = "application/vnd.google-apps.audio";
    public static final String MIME_TYPE_GOOGLE_DRAWING = "application/vnd.google-apps.drawing";
    public static final String MIME_TYPE_GOOGLE_DRIVE_FILE = "application/vnd.google-apps.file";
    public static final String MIME_TYPE_GOOGLE_DRIVE_FOLDER = "application/vnd.google-apps.folder";
    public static final String MIME_TYPE_GOOGLE_FORMS = "application/vnd.google-apps.form";
    public static final String MIME_TYPE_GOOGLE_FUSION_TABLE = "application/vnd.google-apps.fusiontable";
    public static final String MIME_TYPE_GOOGLE_MY_MAPS = "application/vnd.google-apps.map";
    public static final String MIME_TYPE_GOOGLE_PHOTO = "application/vnd.google-apps.photo";
    public static final String MIME_TYPE_GOOGLE_SLIDES = "application/vnd.google-apps.presentation";
    public static final String MIME_TYPE_GOOGLE_SITES = "application/vnd.google-apps.sites";
    public static final String MIME_TYPE_GOOGLE_SHEETS = "application/vnd.google-apps.spreadsheet";
    public static final String MIME_TYPE_GOOGLE_VIDEO = "application/vnd.google-apps.video";
    public static final String MIME_TYPE_GOOGLE_UNKNOWN = "application/vnd.google-apps.unknown";
    private static final String APPLICATION_NAME = "Smart House GGDrive Api";
    private static final List<String> SCOPES = Arrays.asList("https://www.googleapis.com/auth/drive", "email", "profile");
    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static String LOCAL_PORT;
    private static String LOCAL_HOST;
    private static String REDIRECT_URI;
    private static GoogleAuthorizationCodeFlow flow = null;
    private EntityContext entityContext;
    /*
    $mime_types= array(
    "xls" =>'application/vnd.ms-excel',
    "xlsx" =>'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    "xml" =>'text/xml',
    "ods"=>'application/vnd.oasis.opendocument.spreadsheet',
    "csv"=>'text/plain',
    "tmpl"=>'text/plain',
    "pdf"=> 'application/pdf',
    "php"=>'application/x-httpd-php',
    "jpg"=>'image/jpeg',
    "png"=>'image/png',
    "gif"=>'image/gif',
    "bmp"=>'image/bmp',
    "txt"=>'text/plain',
    "doc"=>'application/msword',
    "js"=>'text/js',
    "swf"=>'application/x-shockwave-flash',
    "mp3"=>'audio/mpeg',
    "zip"=>'application/zip',
    "rar"=>'application/rar',
    "tar"=>'application/tar',
    "arj"=>'application/arj',
    "cab"=>'application/cab',
    "html"=>'text/html',
    "htm"=>'text/html',
    "default"=>'application/octet-stream',
    "folder"=>'application/vnd.google-apps.folder'
);
     */
    private GoogleDriveFileSystem.Folder rootFolder;
    private Map<String, String> stringContentMapCache = new HashMap<>();
    private Drive drive;

    @Value("${googleDriveClientSecretFile:SmartHouseGDriveClientSecret.json}")
    private String googleDriveClientSecretFile;

    private GoogleClientSecrets googleClientSecrets;

    {
        // try {
        LOCAL_HOST = InetAddress.getLoopbackAddress().getHostAddress(); //InetAddress.getLocalHost().getHostAddress();
        //  } catch (UnknownHostException e) {
        //     LOCAL_HOST = "localhost";
        // }
        try {
            LOCAL_PORT = ManagementFactory.getPlatformMBeanServer().queryNames(new ObjectName("*:type=Connector,*"), Query.match(Query.attr("protocol"), Query.value("HTTP/1.1"))).iterator().next().getKeyProperty("port");
        } catch (Exception e) {
            LOCAL_PORT = "8080";
        }
        REDIRECT_URI = "http://" + LOCAL_HOST + ":" + LOCAL_PORT + "/rest/loginToCloud";
    }

    public void init() {
      /*  InputStream in = GoogleDriveCloudManager.class.getClassLoader().getResourceAsStream(googleDriveClientSecretFile);
        try {
            googleClientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }*/
    }

    @Override
    public String getBundleId() {
        return "googleDrive";
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    public Folder getRootFolder() throws CodeExchangeException, IOException {
        if (rootFolder == null) {
            rootFolder = new Folder("root", null, null);
        }
        return rootFolder;
    }

    //@Scheduled(fixedDelayString = "${keepGoogleCloudCacheTime:300000}")
    public void clearRootFolder() {
        rootFolder = null;
        stringContentMapCache = new HashMap<>();
    }

    public Drive getDrive() throws IOException, CodeExchangeException {
        return getDrive(null);
    }

    public Drive getDrive(String code) throws IOException, CodeExchangeException {
        if (drive == null) {
            if (code == null) {
                Credential storedCredentials = getStoredCredentials(ADMIN_USER);
                if (storedCredentials != null) {
                    drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, storedCredentials).setApplicationName(APPLICATION_NAME).build();
                } else {
                    throw new CodeExchangeException(getAuthorizationUrl(ADMIN_USER/*userManager.getSharedEmail()*/));
                }
            } else {
                UserEntity userEntity = entityContext.getEntity(ADMIN_USER);
                Credential credentials = getCredentials(code, userEntity == null ? null : userEntity.getUserId());
                drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials).setApplicationName(APPLICATION_NAME).build();
            }
        }
        return drive;
    }

    public Boolean isLoggedInGoogleOauth2() {
        try {
            getDrive();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void login(String code) throws GoogleDriveFileSystem.CodeExchangeException {
        try {
            getDrive(code);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Credential getStoredCredentials(String userName) {
        UserEntity userEntity = entityContext.getEntity("u_" + userName);
        if (userEntity != null) {
            GoogleCredential credential = new GoogleCredential.Builder().setJsonFactory(JSON_FACTORY).setTransport(HTTP_TRANSPORT).setClientSecrets(googleClientSecrets).build();
            credential.setAccessToken(userEntity.getGoogleDriveAccessToken());
            credential.setRefreshToken(userEntity.getGoogleDriveRefreshToken());
            return credential;
        }
        return null;
    }

    void storeCredentials(String userId, Credential credentials) {
        UserEntity userEntity = entityContext.getEntity(ADMIN_USER);
        userEntity.setUserId(userId);
        userEntity.setGoogleDriveAccessToken(credentials.getAccessToken());
        userEntity.setGoogleDriveRefreshToken(credentials.getRefreshToken());
        entityContext.save(userEntity);
    }

    /**
     * Build an authorization flow and store it as a static class attribute.
     *
     * @return GoogleAuthorizationCodeFlow instance.
     */
    GoogleAuthorizationCodeFlow getFlow() {
        if (flow == null) {
            flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, googleClientSecrets, SCOPES).setAccessType("offline").setApprovalPrompt("force").build();
        }
        return flow;
    }

    /**
     * Exchange an authorization code for OAuth 2.0 credentials.
     *
     * @param authorizationCode Authorization code to exchange for OAuth 2.0
     *                          credentials.
     * @return OAuth 2.0 credentials.
     */
    Credential exchangeCode(String authorizationCode) throws IOException {
        GoogleTokenResponse response = getFlow().newTokenRequest(authorizationCode).setRedirectUri(REDIRECT_URI).setScopes(Collections.singletonList("https://www.googleapis.com/auth/drive")).execute();
        return getFlow().createAndStoreCredential(response, null);
    }

    /**
     * Send a request to the UserInfo API to retrieve the user's information.
     *
     * @param credentials OAuth 2.0 credentials to authorize the request.
     * @return User's information.
     * @throws NoUserIdException An error occurred.
     */
    private Userinfoplus getUserInfo(Credential credentials) throws NoUserIdException {
        Oauth2 userInfoService = new Oauth2.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials).setApplicationName(APPLICATION_NAME).build();
        Userinfoplus userInfo = null;
        try {
            userInfo = userInfoService.userinfo().get().execute();
        } catch (IOException e) {
            System.err.println("An error occurred: " + e);
        }
        if (userInfo != null && userInfo.getId() != null) {
            return userInfo;
        } else {
            throw new NoUserIdException();
        }
    }

    private String getAuthorizationUrl(String emailAddress) {
        return getFlow().newAuthorizationUrl().setRedirectUri(REDIRECT_URI).setClientId(googleClientSecrets.getWeb().getClientId()).set("user_id", emailAddress).build();
    }

    private Credential getCredentials(String authorizationCode, String userId)
            throws IOException {
        try {
            Credential credentials = exchangeCode(authorizationCode);
            if (userId == null) {
                userId = getUserInfo(credentials).getId();
            }
            storeCredentials(userId, credentials);
            return credentials;
        } catch (NoUserIdException e) {
            throw new RuntimeException(e);
        }
    }

    public String getCloudFileContent(String fileID) {
        try {
            String s = stringContentMapCache.get(fileID);
            if (s == null) {
                InputStream inputStream = getDrive().files().get(fileID).executeMediaAsInputStream();
                List<String> list = IOUtils.readLines(inputStream);
                s = StringUtils.join(list, "\n");
                stringContentMapCache.put(fileID, s);
            }
            return s;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public InputStream getFileInputStream(String fileID) {
        try {
            return getDrive().files().get(fileID).executeMediaAsInputStream();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public File getFile(String fileID) {
        try {
            return getDrive().files().get(fileID).execute();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Integer getPropertyValueInteger(File file, String key) {
        return getPropertyValueInteger(file, key, 0);
    }

    Integer getPropertyValueInteger(File file, String key, Integer defValue) {
        String propertyValue = getPropertyValue(file, key);
        return propertyValue == null ? defValue : Integer.parseInt(propertyValue);
    }

    private boolean getPropertyValueBoolean(File file, String key) {
        String propertyValue = getPropertyValue(file, key);
        return Boolean.parseBoolean(propertyValue);
    }

    private String getPropertyValue(File file, String key) {
        return getPropertyValue(file, key, null);
    }

    public String getPropertyValue(File file, String key, String defValue) {
        // Property property = getProperty(file, key);
        //  return property == null ? defValue : property.getValue();
        return "";
    }

 /*   private Property getProperty(File file, String key) {
        if (file.getProperties() != null) {
            for (Property property : file.getProperties()) {
                if (property.getKey().equals(key)) {
                    return property;
                }
            }
        }
        return null;
    }*/

    Object getPropertyValue(Class<?> type, File file, String key) {
        if (type.isAssignableFrom(Integer.class)) {
            return getPropertyValueInteger(file, key);
        }
        if (type.isAssignableFrom(Boolean.class)) {
            return getPropertyValueBoolean(file, key);
        }
        return getPropertyValue(file, key);
    }

    /**
     * Exception thrown when a code exchange has failed.
     */
    public static class CodeExchangeException extends Exception {

        String authorizationUrl;

        CodeExchangeException(String authorizationUrl) {
            this.authorizationUrl = authorizationUrl;
        }

        public String getAuthorizationUrl() {
            return authorizationUrl;
        }
    }

    /**
     * Exception thrown when no user ID could be retrieved.
     */
    private static class NoUserIdException extends Exception {
    }

    public class Folder {
        private final Folder parent;
        private final String fileID;
        private File file;
        private String folderName;
        // private List<ChildReference> childReferences;
        private Map<String, File> fileMapCache = new HashMap<>();
        private Map<String, Folder> folderMapCache = new HashMap<>();

        private Folder(String fileID, Folder parent, File file) {
            this.fileID = fileID;
            this.parent = parent;
            this.file = file;
        }


       /* List<ChildReference> getChildReferences() throws CodeExchangeException, IOException {
            if (childReferences == null) {
                childReferences = getDrive().children().list(fileID).execute().getItems();
            }
            return childReferences;
        }*/

      /*  private File createFolder(String name) throws IOException {
            File fileMetadata = new File();
            fileMetadata.setParents(Collections.singletonList(new ParentReference().setId(fileID)));
            fileMetadata.setTitle(name);
            fileMetadata.setMimeType(MIME_TYPE_GOOGLE_DRIVE_FOLDER);
            return drive.files().insert(fileMetadata).setFields("id").execute();
        }
*/
      /*  Folder getChildFolder(String folderName, boolean isCreateIfNotExists) throws IOException, CodeExchangeException {
            File childFile = getFileByName(folderName);
            if (childFile != null) {
                Folder folder = folderMapCache.get(childFile.getId());
                if (folder == null) {
                    folder = new Folder(childFile.getId(), this, childFile);
                    folderMapCache.put(childFile.getId(), folder);
                }
                return folder;
            }
            if (isCreateIfNotExists) {
                createFolder(folderName);
                rootFolder = null;
                return new Folder(file.getId(), this, file);
            }
            return null;
        }
*/
     /*   List<File> getAllFiles() {
            try {
                List<File> files = new ArrayList<>();
                for (ChildReference childReference : getChildReferences()) {
                    files.add(getFileByFieldID(childReference.getId()));
                }
                return files;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }*/

        public File getFile() {
            return file;
        }

        private File getFileByFieldID(String fieldID) throws CodeExchangeException, IOException {
            File file = fileMapCache.get(fieldID);
            if (file == null) {
                file = getDrive().files().get(fieldID).execute();
                fileMapCache.put(fieldID, file);
            }
            return file;
        }

      /*  File addOrUpdateFile(Path tempFile, String mimeType, String title, String description, List<Property> properties) throws IOException, CodeExchangeException {
            return insertOrUpdateFile(title, description, new FileContent(mimeType, tempFile.toFile()), properties);
        }

        File addOrUpdateFile(String title, String description, List<Property> properties) throws IOException, CodeExchangeException {
            return insertOrUpdateFile(title, description, null, properties);
        }*/

      /*  private File getFileByNameOrNew(String fileName) throws CodeExchangeException, IOException {
            File file = getFileByName(fileName);
            return file == null ? new File() : file;
        }*/

      /*  private File getFileByName(String fileName) throws CodeExchangeException, IOException {
            for (ChildReference childReference : getChildReferences()) {
                File file = getFileByFieldID(childReference.getId());
                if (Objects.equals(file.getTitle(), fileName)) {
                    return file;
                }
            }
            return null;
        }*/

   /*     private File insertOrUpdateFile(String title, String description, FileContent mediaContent, List<Property> properties) throws IOException, CodeExchangeException {
            title += ".txt";
            File fileMetadata = getFileByNameOrNew(title);

            if (properties.size() > 0) {
                fileMetadata.setProperties(properties);
            }
            if (description != null) {
                fileMetadata.setDescription(description);
            }
            fileMetadata.setModifiedDate(new DateTime(new Date()));
            if (fileMetadata.getId() == null) {
                fileMetadata.setTitle(title);
                fileMetadata.setParents(Collections.singletonList(new ParentReference().setId(fileID)));
                if (mediaContent != null) {
                    fileMetadata = drive.files().insert(fileMetadata, mediaContent).setFields("id").execute();
                } else {
                    fileMetadata = drive.files().insert(fileMetadata).setFields("id").execute();
                }
            } else {
                if (mediaContent != null) {
                    fileMetadata = getDrive().files().update(fileMetadata.getId(), fileMetadata, mediaContent).execute();
                } else {
                    fileMetadata = getDrive().files().update(fileMetadata.getId(), fileMetadata).execute();
                }
            }
            rootFolder = null;
            stringContentMapCache = new HashMap<>();
            return fileMetadata;
        }*/
    }
}
