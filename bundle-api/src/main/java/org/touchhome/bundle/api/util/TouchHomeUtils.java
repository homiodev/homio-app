package org.touchhome.bundle.api.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.touchhome.bundle.api.exception.NotFoundException;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

@Log4j2
public class TouchHomeUtils {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path TMP_FOLDER = Paths.get(FileUtils.getTempDirectoryPath());
    public static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private static Path rootPath = SystemUtils.getUserHome().toPath().resolve("state");

    static {
        TouchHomeUtils.createDirectoriesIfNotExists(rootPath);
    }

    @SneakyThrows
    public static <T> T readAndMergeJSON(String resource, T targetObject) {
        ObjectReader updater = OBJECT_MAPPER.readerForUpdating(targetObject);
        Enumeration<URL> resources = TouchHomeUtils.class.getClassLoader().getResources(resource);
        while (resources.hasMoreElements()) {
            updater.readValue(resources.nextElement());
        }
        return targetObject;
    }

    @SneakyThrows
    public static <T> List<T> readJSON(String resource, Class<T> targetClass) {
        Enumeration<URL> resources = TouchHomeUtils.class.getClassLoader().getResources(resource);
        List<T> list = new ArrayList<>();
        while (resources.hasMoreElements()) {
            list.add(OBJECT_MAPPER.readValue(resources.nextElement(), targetClass));
        }
        return list;
    }

    public static ResponseEntity<InputStreamResource> inputStreamToResource(InputStream stream, MediaType contentType) {
        try {
            return ResponseEntity.ok()
                    .contentLength(stream.available())
                    .contentType(contentType)
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public static String getTimestampString() {
        return getTimestampString(new Date());
    }

    private static String getTimestampString(Date date) {
        return dateFormat.format(date);
    }

    public static List<Date> range(Date minDate, Date maxDate) {
        long time = (maxDate.getTime() - minDate.getTime()) / 10;
        List<Date> dates = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            dates.add(new Date(minDate.getTime() + time * i));
        }
        return dates;
    }

    public static List<String> readFile(String fileName) {
        try {
            return IOUtils.readLines(TouchHomeUtils.class.getClassLoader().getResourceAsStream(fileName));
        } catch (Exception ex) {
            log.error(TouchHomeUtils.getErrorMessage(ex), ex);

        }
        return Collections.emptyList();
    }

    @SneakyThrows
    public static void copyResource(String fileName, Path targetPath) {
        Files.copy(Objects.requireNonNull(TouchHomeUtils.class.getClassLoader().getResourceAsStream(fileName)),
                targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    public static Path path(String path) {
        return rootPath.resolve(path);
    }

    private static String getSimpleNameFromMethod(Method method) {
        String fieldName = method.getName().startsWith("get") ? method.getName().substring(3) : (method.getName().startsWith("is") ? method.getName().substring(2) : method.getName());
        char c[] = fieldName.toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        fieldName = new String(c);
        return fieldName;
    }

    public static String getErrorMessage(Throwable ex) {
        if (ex.getCause() instanceof NullPointerException) {
            return ex.getStackTrace()[0].toString();
        }
        if (ex.getCause() instanceof NoSuchFileException) {
            return "File or directory " + ((NoSuchFileException) ex.getCause()).getFile() + " does not exists.";
        }
        return ex.getCause() == null ? ex.getMessage() : ex.getCause().getLocalizedMessage();
    }

    public static String toTmpFile(String uniqueID, String suffix, ByteArrayOutputStream outputStream) throws IOException {
        Path tempFile = Files.createTempFile(uniqueID, suffix);
        Files.write(tempFile, outputStream.toByteArray());
        return "rest/download/tmp/" + TMP_FOLDER.relativize(tempFile).toString();
    }

    public static Path fromTmpFile(String str) {
        Path path = TMP_FOLDER.resolve(str);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Unable to find file: " + str);
        }
        return path;
    }

    @SneakyThrows
    public static FileSystem getOrCreateNewFileSystem(String fileSystemPath) {
        if (fileSystemPath == null) {
            return FileSystems.getDefault();
        }
        try {
            return FileSystems.getFileSystem(URI.create(fileSystemPath));
        } catch (Exception ex) {
            return FileSystems.newFileSystem(URI.create(fileSystemPath), Collections.emptyMap());
        }
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    public static Path resolvePath(String... path) {
        Path relativePath = Paths.get(rootPath.toString(), path);
        if (Files.notExists(relativePath)) {
            try {
                Files.createDirectories(relativePath);
            } catch (Exception ex) {
                log.error("Unable to create path: <{}>", relativePath);
                throw new RuntimeException("Unable to create path: " + relativePath);
            }
        }
        return relativePath;
    }

    @SneakyThrows
    private static void createDirectoriesIfNotExists(Path path) {
        if (Files.notExists(path)) {
            try {
                Files.createDirectory(path);
            } catch (Exception ex) {
                log.error("Unable to create path: <{}>", path.toAbsolutePath().toString());
            }
        }
    }

    /**
     * Loads library from current JAR archive
     * <p>
     * The file from JAR is copied into system temporary directory and then loaded. The temporary file is deleted after exiting.
     * Method uses String as filename because the pathname is "abstract", not system-dependent.
     *
     * @param path The filename inside JAR as absolute path (beginning with '/'), e.g. /package/File.ext
     * @throws IOException              If temporary file creation or read/write operation fails
     * @throws IllegalArgumentException If source file (param path) does not exist
     * @throws IllegalArgumentException If the path is not absolute or if the filename is shorter than three characters (restriction of {@see File#createTempFile(java.lang.String, java.lang.String)}).
     */
    public static void loadLibraryFromJar(String path) throws IOException {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("The path has to be absolute (start with '/').");
        }

        // Obtain filename from path
        String[] parts = path.split("/");
        String filename = (parts.length > 1) ? parts[parts.length - 1] : null;

        // Split filename to prexif and suffix (extension)
        String prefix = "";
        String suffix = null;
        if (filename != null) {
            parts = filename.split("\\.", 2);
            prefix = parts[0];
            suffix = (parts.length > 1) ? "." + parts[parts.length - 1] : null; // Thanks, davs! :-)
        }

        // Check if the filename is okay
        if (filename == null || prefix.length() < 3) {
            throw new IllegalArgumentException("The filename has to be at least 3 characters long.");
        }

        // Prepare temporary file
        File temp = File.createTempFile(prefix, suffix);
        temp.deleteOnExit();

        if (!temp.exists()) {
            throw new FileNotFoundException("File " + temp.getAbsolutePath() + " does not exist.");
        }

        // Open and check input stream
        try (InputStream is = TouchHomeUtils.class.getResourceAsStream(path);
             OutputStream os = new FileOutputStream(temp)) {
            if (is == null) {
                throw new FileNotFoundException("File " + path + " was not found inside JAR.");
            }
            IOUtils.copy(is, os);
        }
        System.load(temp.getAbsolutePath());
    }


    @SneakyThrows
    public static Map<String, String> readPropertiesMerge(String path) {
        Map<String, String> map = new HashMap<>();
        readProperties(path).forEach(map::putAll);
        return map;
    }

    @SneakyThrows
    public static List<Map<String, String>> readProperties(String path) {
        Enumeration<URL> resources = TouchHomeUtils.class.getClassLoader().getResources(path);
        List<Map<String, String>> properties = new ArrayList<>();
        while (resources.hasMoreElements()) {
            try (InputStream input = resources.nextElement().openStream()) {
                Properties prop = new Properties();
                prop.load(input);
                properties.add(new HashMap(prop));
            }
        }
        return properties;
    }

    public static Method findRequreMethod(Class cl, String name) {
        for (Class<?> current = cl; current != null; current = current.getSuperclass()) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new NotFoundException("Unable to find method: " + name + " in class: " + cl.getSimpleName());
    }

    public static boolean containsAny(int[] array, Integer value) {
        for (int i : array) {
            if (i == value) {
                return true;
            }
        }
        return false;
    }
}
