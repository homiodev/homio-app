package org.touchhome.app.extloader;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.reflections.Reflections;
import org.xeustechnologies.jcl.ClasspathResources;
import org.xeustechnologies.jcl.Configuration;
import org.xeustechnologies.jcl.JarClassLoader;
import org.xeustechnologies.jcl.ProxyClassLoader;
import org.xeustechnologies.jcl.exception.JclException;
import sun.misc.URLClassPath;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class BundleClassLoader extends JarClassLoader {

    private static final boolean IGNORE_JAR_VERSION_DEFAULT = true;

    private final Path baseDir;
    private static List<String> systemClasspathJars;

    static {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (!(classLoader instanceof URLClassLoader)) {
            throw new RuntimeException("Unable read system classpath jars due classLoader isn not instance of URLClassLoader");
        }
        systemClasspathJars = Stream.of(((URLClassLoader) classLoader).getURLs())
                .map(url -> new File(url.getPath()).getName())
                .filter(ExtUtil::isJar).collect(Collectors.toList());
    }

    BundleClassLoader(Path baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Register single folder or jar file. Must be relative path
     */
    void register(String notQualifiedPath) {
        try {
            Path pathToClasses = baseDir.resolve(notQualifiedPath);
            if (ExtUtil.isJar(pathToClasses.toString())) { // handle with .jar
                String jarFileName = pathToClasses.getFileName().toString();
                if (!isIgnoreRequireExcludeJar(jarFileName) && !isIgnoreJar(jarFileName)) {
                    log.info("Adding jar <{}> to classpath", jarFileName);
                    InternalJarClassLoader internalJarClassLoader = new InternalJarClassLoader(pathToClasses);
                    // internalJarClassLoader.loadAllClasses(notQualifiedPath);
                    super.addLoader(internalJarClassLoader);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public URL getResource(String name) {
        try {
            return super.getResource(name);
        } catch (JclException ex) {
            throw ex;
            //return new URLClassLoader(nonJarURLs.toArray(new URL[nonJarURLs.size()])).getResource(name);
        }
    }

    public <A extends Annotation> Class<A> resolve(Class<A> configurationClass) {
        try {
            return loadClass(configurationClass.getName());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Find all jars recursivelly starting from baseDir, create classLoader and register into BundleClassLoader
     */
    void registerAllJarFromBaseDir(Reflections reflections) {
        reflections.getResources(ExtUtil::isJar).forEach(this::register);
    }


    void prepareForSpring() {
        getLocalLoader().setEnabled(false);
        getSystemLoader().setEnabled(false);
        getParentLoader().setEnabled(false);
        getCurrentLoader().setEnabled(false);

        super.addLoader(new SystemLoader(getClass().getClassLoader()));
    }

    private boolean isJarExistsInClasspath(String jarPrefix) {
        return systemClasspathJars.stream().anyMatch(s -> s.startsWith(jarPrefix));
    }

    private String getNotRequiredJarExistsInClasspath(String jarFileName) {
        return systemClasspathJars.stream().filter(s -> getJarName(s,
                IGNORE_JAR_VERSION_DEFAULT).equals(getJarName(jarFileName, IGNORE_JAR_VERSION_DEFAULT)))
                .findAny().orElse(null);
    }

    private boolean isIgnoreRequireExcludeJar(String jarFileName) {
        Optional<String> jarInExcludeOptional = REQUIRED_EXCLUDE_JARS.stream().filter(jarFileName::startsWith).findAny();
        if (jarInExcludeOptional.isPresent()) {
            if (isJarExistsInClasspath(jarInExcludeOptional.get())) {
                log.info("Ignore require jar <{}> to classloader due it exists in system classpath.", jarFileName);
                return true;
            } else {
                log.error("Jar file <{}> was detected as exclude, but not presents in system classpath.", jarFileName);
                return true;
            }
        }
        return false;
    }

    private boolean isIgnoreJar(String jarFileName) {
        boolean mayIgnoreJar = IGNORE_JAR_PREFIXES.stream().anyMatch(jarFileName::startsWith);
        if (mayIgnoreJar) {
            String jarInClasspath = getNotRequiredJarExistsInClasspath(jarFileName);
            if (jarInClasspath != null) {
                log.info("Ignore jar <{}> to classpath due <{}> exists in system classpath", jarFileName, jarInClasspath);
                return true;
            }
        }
        return false;
    }

    private String getJarName(String jarFileName, boolean ignoreJarVersion) {
        if (ignoreJarVersion) {
            Matcher matcher = Pattern.compile(".*-").matcher(jarFileName);
            return matcher.find() ? matcher.group() : jarFileName;
        }
        return jarFileName;
    }

    public class InternalJarClassLoader extends ProxyClassLoader {
        private final URLClassPath ucp;
        private final ClasspathResources classpathResources = new ClasspathResources();
        final Map<String, Class> classes = new HashMap<>();

        @SneakyThrows
        InternalJarClassLoader(Path pathToClasses) {
            this.classpathResources.loadResource(pathToClasses.toString());
            this.ucp = new URLClassPath(new URL[]{pathToClasses.toUri().toURL()});
        }

        @SneakyThrows
        void loadAllClasses(String pathToJar) {
            JarFile jarFile = new JarFile(pathToJar);
            Enumeration<JarEntry> e = jarFile.entries();

            URL[] urls = {new URL("jar:file:" + pathToJar + "!/")};
            URLClassLoader cl = URLClassLoader.newInstance(urls);
            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                if (je.isDirectory() || !je.getName().endsWith(".class")) {
                    continue;
                }
                String className = je.getName().substring(0, je.getName().length() - 6);
                className = className.replace('/', '.');
                classes.put(className, cl.loadClass(className));
            }
        }

        @Override
        public Class loadClass(String className, boolean resolveIt) {
            if (!classes.containsKey(className)) {
                byte[] classBytes = classpathResources.getResource(formatClassName(className));
                if (classBytes != null) {
                    Class result = defineClass(className, classBytes, 0, classBytes.length);

                    if (result != null) {
                        if (result.getPackage() == null) {
                            int lastDotIndex = className.lastIndexOf('.');
                            String packageName = (lastDotIndex >= 0) ? className.substring(0, lastDotIndex) : "";
                            definePackage(packageName, null, null, null, null, null, null, null);
                        }
                        if (resolveIt) {
                            resolveClass(result);
                        }
                        classes.put(className, result);
                    }
                }
            }
            return classes.get(className);
        }

        @Override
        public InputStream loadResource(String name) {
            byte[] arr = classpathResources.getResource(name);
            if (arr != null) {
                return new ByteArrayInputStream(arr);
            }

            return null;
        }

        @Override
        public URL findResource(String name) {
            URL url = classpathResources.getResourceURL(name);
            if (url != null) {
                return url;
            }
            return ucp.findResource(name, true);
        }
    }

    class SystemLoader extends ProxyClassLoader {
        private ClassLoader appClassLoader;

        SystemLoader(ClassLoader appClassLoader) {
            this.order = 50;
            this.enabled = Configuration.isSystemLoaderEnabled();
            this.appClassLoader = appClassLoader;
        }

        @Override
        public Class loadClass(String className, boolean resolveIt) {
            try {
                return appClassLoader.loadClass(className);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        @Override
        public InputStream loadResource(String name) {
            return appClassLoader.getResourceAsStream(name);
        }

        @Override
        public URL findResource(String name) {
            return ClassLoader.getSystemResource(name);
            /*if (name.startsWith("ws/") || name.startsWith("com/imaginesoftware")) { // ignore all that starts with ws/ or com/imaginesoftware due we don't want include classes from batch-web-services project!
                return null;
            }
            return ClassLoader.getSystemResource(name);*/
        }
    }

    // to avoid duplicates in classpath we may ignore jars with some prefixes, but it's only optional
    private List<String> IGNORE_JAR_PREFIXES = Arrays.asList(
            "spring", "commons", "jackson", "java", "log4j"
    );

    private List<String> REQUIRED_EXCLUDE_JARS = Arrays.asList(
            "spring-context", "spring-core", "spring-beans", "spring-security", "spring-expression", "spring-web", "spring-util",
            "javax.servlet-api", "servlet-api-"
    );
}
