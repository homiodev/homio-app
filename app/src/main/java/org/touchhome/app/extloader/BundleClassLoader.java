package org.touchhome.app.extloader;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.xeustechnologies.jcl.ClasspathResources;
import org.xeustechnologies.jcl.Configuration;
import org.xeustechnologies.jcl.JarClassLoader;
import org.xeustechnologies.jcl.ProxyClassLoader;
import sun.misc.URLClassPath;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Log4j2
public class BundleClassLoader extends JarClassLoader {

    void register(Path jarPath) {
        if (ExtUtil.isJar(jarPath.toString())) {
            String jarFileName = jarPath.getFileName().toString();
            log.info("Adding jar <{}> to classpath", jarFileName);
            InternalJarClassLoader internalJarClassLoader = new InternalJarClassLoader(jarPath);
            super.addLoader(internalJarClassLoader);
        }
    }

    @Override
    public URL getResource(String name) {
        return super.getResource(name);
    }

    void prepareForSpring() {
        getLocalLoader().setEnabled(false);
        getSystemLoader().setEnabled(false);
        getParentLoader().setEnabled(false);
        getCurrentLoader().setEnabled(false);

        super.addLoader(new SystemLoader(getClass().getClassLoader()));
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
        }
    }
}
