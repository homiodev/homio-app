package org.touchhome.bundle.api.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.touchhome.bundle.api.exception.NotFoundException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

@Log4j2
public class TouchHomeUtils {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String[] SYSTEM_BUNDLES = {"arduino", "raspberry", "telegram", "zigbee", "cloud", "bluetooth", "xaomi"};
    private static final Path TMP_FOLDER = Paths.get(FileUtils.getTempDirectoryPath());
    @Getter
    private static final Path filesPath;
    @Getter
    private static final Path bundlePath;
    @Getter
    private static final Path sshPath;
    public static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private static Path rootPath;
    private static Map<String, ClassLoader> bundleClassLoaders = new HashMap();

    static {
        if (SystemUtils.IS_OS_WINDOWS) {
            rootPath = SystemUtils.getUserHome().toPath().resolve("touchhome");
        } else {
            rootPath = Paths.get("/opt/touchhome");
        }
        filesPath = getOrCreatePath("asm_files");
        sshPath = getOrCreatePath("ssh");
        bundlePath = getOrCreatePath("bundles");
    }

    public static void addClassLoader(String bundleName, ClassLoader classLoader) {
        bundleClassLoaders.put(bundleName, classLoader);
    }

    public static void removeClassLoader(String bundleName) {
        bundleClassLoaders.remove(bundleName);
    }

    @SneakyThrows
    public static <T> T readAndMergeJSON(String resource, T targetObject) {
        ObjectReader updater = OBJECT_MAPPER.readerForUpdating(targetObject);
        ArrayList<ClassLoader> classLoaders = new ArrayList<>(bundleClassLoaders.values());
        classLoaders.add(TouchHomeUtils.class.getClassLoader());

        for (ClassLoader classLoader : classLoaders) {
            for (URL url : Collections.list(classLoader.getResources(resource))) {
                updater.readValue(url);
            }
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

    public static Path path(String path) {
        return rootPath.resolve(path);
    }

    public static String getErrorMessage(Throwable ex) {
        if (ex.getCause() instanceof NullPointerException) {
            return ex.getStackTrace()[0].toString();
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
    private static Path createDirectoriesIfNotExists(Path path) {
        if (Files.notExists(path)) {
            try {
                Files.createDirectory(path);
            } catch (Exception ex) {
                log.error("Unable to create path: <{}>", path.toAbsolutePath().toString());
            }
        }
        return path;
    }

    @SneakyThrows
    public static Map<String, String> readPropertiesMerge(String path) {
        Map<String, String> map = new HashMap<>();
        readProperties(path).forEach(map::putAll);
        return map;
    }

    @SneakyThrows
    private static List<Map<String, String>> readProperties(String path) {
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

    public static TemplateBuilder templateBuilder(String templateName) {
        return new TemplateBuilder(templateName);
    }

    private static Path getOrCreatePath(String path) {
        return TouchHomeUtils.createDirectoriesIfNotExists(rootPath.resolve(path));
    }

    public static class TemplateBuilder {
        private final Context context = new Context();
        private final TemplateEngine templateEngine;
        private final String templateName;

        TemplateBuilder(String templateName) {
            this.templateName = templateName;
            this.templateEngine = new TemplateEngine();
            ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
            templateResolver.setTemplateMode(TemplateMode.HTML);
            templateEngine.setTemplateResolver(templateResolver);
        }

        public TemplateBuilder set(String key, Object value) {
            context.setVariable(key, value);
            return this;
        }

        public String build() {
            StringWriter stringWriter = new StringWriter();
            templateEngine.process("templates/" + templateName, context, stringWriter);
            return stringWriter.toString();
        }
    }
}
