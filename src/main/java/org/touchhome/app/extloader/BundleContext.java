package org.touchhome.app.extloader;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.touchhome.bundle.api.BundleConfiguration;
import org.touchhome.bundle.api.BundleEntryPoint;
import org.touchhome.common.exception.ServerException;
import org.touchhome.common.util.SpringUtils;
import sun.reflect.ReflectionFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Getter
@Setter
public class BundleContext {
    private static MavenXpp3Reader pomReader = new MavenXpp3Reader();

    private final Path bundleContextFile; // batch context file associated with this context
    private final Model pomFile;
    private final Manifest manifest;
    private String bundleID;

    private boolean internal;
    private boolean installed;
    private BundleSpringContext config;

    @SneakyThrows
    BundleContext(Path bundleContextFile) {
        this.bundleContextFile = bundleContextFile;
        ZipFile zipFile = new ZipFile(bundleContextFile.toString());
        JarInputStream jarStream = new JarInputStream(new FileInputStream(bundleContextFile.toString()));
        this.manifest = jarStream.getManifest();
        this.pomFile = readPomFile(zipFile);
        jarStream.close();
        zipFile.close();
    }

    BundleContext(String bundleID) {
        this.bundleID = bundleID;
        this.bundleContextFile = null;
        this.pomFile = null;
        this.manifest = null;
        this.internal = true;
    }

    private Model readPomFile(ZipFile file) throws IOException, XmlPullParserException {
        String artifactId = this.manifest.getMainAttributes().getValue("artifactId");
        for (ZipEntry e : Collections.list(file.entries())) {
            if (e.getName().endsWith(artifactId + "/pom.xml")) {
                return pomReader.read(file.getInputStream(e));
            }
        }
        throw new ServerException("Unable to find pom.xml in jar");
    }

    public Set<String> getDependencies() {
        return this.pomFile.getDependencies().stream()
                .filter(d -> d.getGroupId().equals("org.touchhome") && d.getArtifactId().contains("bundle"))
                .map(Dependency::getArtifactId).collect(Collectors.toSet());
    }

    @SneakyThrows
    void load(ConfigurationBuilder configurationBuilder, Environment env, ApplicationContext parentContext, ClassLoader classLoader) {
        URL bundleUrl = bundleContextFile.toUri().toURL();
        Reflections reflections = new Reflections(configurationBuilder.setUrls(bundleUrl));

        config = new BundleSpringContext(env);
        bundleID = config.fetchBundleID(reflections, pomFile.getArtifactId());
        config.configureSpringContext(reflections, parentContext, bundleID, classLoader);
    }

    void destroy() {
        config.destroy();
        getAllBundleClassLoader().destroy(bundleID);
    }

    public boolean isLoaded() {
        return config != null;
    }

    public String getBundleFriendlyName() {
        return StringUtils.defaultString(this.pomFile.getName(), bundleID);
    }

    public ApplicationContext getApplicationContext() {
        return config.ctx;
    }

    public String getBasePackage() {
        return this.config.configClass.getPackage().getName();
    }

    public String getVersion() {
        String version = this.pomFile.getVersion();
        if (version == null && this.pomFile.getParent() != null) {
            version = this.pomFile.getParent().getVersion();
        }
        if (version == null) {
            throw new ServerException("Unable to find version for bundle: " + bundleID);
        }
        return version;
    }

    private BundleClassLoaderHolder getAllBundleClassLoader() {
        return this.config.bundleClassLoaderHolder;
    }

    public SingleBundleClassLoader getBundleClassLoader() {
        return getAllBundleClassLoader().getBundleClassLoader(bundleID);
    }

    @RequiredArgsConstructor
    private static class BundleSpringContext {

        private final Environment env;
        private AnnotationConfigApplicationContext ctx;
        private Class<?> configClass;
        private BundleClassLoaderHolder bundleClassLoaderHolder;

        @SneakyThrows
        private static <T> T createClassInstance(Class<T> clazz, Class<? super T> parent) {
            ReflectionFactory rf = ReflectionFactory.getReflectionFactory();
            Constructor objDef = parent.getDeclaredConstructor();
            Constructor intConstr = rf.newConstructorForSerialization(clazz, objDef);
            return clazz.cast(intConstr.newInstance());
        }

        public String fetchBundleID(Reflections reflections, String artifactId) {
            Set<Class<? extends BundleEntryPoint>> bundleEntryPoints = reflections.getSubTypesOf(BundleEntryPoint.class);
            if (bundleEntryPoints.isEmpty()) {
                throw new ServerException("Found no BundleEntryPoint in context of bundle: " + artifactId);
            }
            if (bundleEntryPoints.size() > 1) {
                throw new ServerException("Found multiple BundleEntryPoint in context of bundle: " + artifactId);
            }
            Class<? extends BundleEntryPoint> bundleEntrypointClass = bundleEntryPoints.iterator().next();

            BundleEntryPoint bundleEntrypoint = createClassInstance(bundleEntrypointClass, Object.class);
            return bundleEntrypoint.getBundleId();
        }

        void configureSpringContext(Reflections reflections, ApplicationContext parentContext, String bundleID, ClassLoader classLoader) {
            configClass = findBatchConfigurationClass(reflections);
            bundleClassLoaderHolder = parentContext.getBean(BundleClassLoaderHolder.class);
            bundleClassLoaderHolder.setClassLoaders(bundleID, classLoader);
            BundleConfiguration bundleConfiguration = configClass.getDeclaredAnnotation(BundleConfiguration.class);

            // create spring context
            ctx = new AnnotationConfigApplicationContext();
            ctx.setId(bundleID);
            ctx.setParent(parentContext);
            ctx.setClassLoader(classLoader);
            ctx.setResourceLoader(new PathMatchingResourcePatternResolver(classLoader));

            // set custom environments
            Map<String, Object> customEnv = Stream.of(bundleConfiguration.env()).collect(
                    Collectors.toMap(BundleConfiguration.Env::key, e -> SpringUtils.replaceEnvValues(e.value(), env::getProperty)));

            if (!customEnv.isEmpty()) {
                ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("BundleConfiguration PropertySource", customEnv));
            }

            // wake up spring context
            ctx.scan(configClass.getPackage().getName());
            ctx.register(configClass);

            ctx.refresh();
            ctx.start();
        }

        /**
         * Find spring configuration class with annotation @BundleConfiguration and @Configuration
         */
        private Class<?> findBatchConfigurationClass(Reflections reflections) {
            // find configuration class
            Set<Class<?>> springConfigClasses = reflections.getTypesAnnotatedWith(BundleConfiguration.class);
            if (springConfigClasses.isEmpty()) {
                throw new ServerException("Configuration class with annotation @BundleConfiguration not found. Not possible to create spring context");
            }
            if (springConfigClasses.size() > 1) {
                throw new ServerException("Configuration class with annotation @BundleConfiguration must be unique, but found: " + StringUtils.join(springConfigClasses, ", "));
            }
            Class<?> batchConfigurationClass = springConfigClasses.iterator().next();
            if (batchConfigurationClass.getDeclaredAnnotation(BundleConfiguration.class) == null) {
                throw new ServerException("Loaded batch definition has different ws-service-api.jar version and can not be instantiated");
            }
            return batchConfigurationClass;
        }

        void destroy() {
            ctx.close();
        }
    }
}
