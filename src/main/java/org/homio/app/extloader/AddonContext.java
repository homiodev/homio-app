package org.homio.app.extloader;

import com.pivovarit.function.ThrowingRunnable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.homio.api.AddonConfiguration;
import org.homio.api.exception.ServerException;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.SpringUtils;
import org.homio.app.HomioClassLoader;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Log4j2
@Getter
@Setter
public class AddonContext {

    private final List<ThrowingRunnable<Exception>> destryListeners = new ArrayList<>();
    private static MavenXpp3Reader pomReader = new MavenXpp3Reader();

    private final Path contextFile; // batch context file associated with this context
    private final Model pomFile;
    private final Manifest manifest;
    private String addonID;

    private boolean internal;
    private boolean initialized;
    private AddonSpringContext config;
    private String loadError;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AddonContext that = (AddonContext) o;

        if (!addonID.equals(that.addonID)) {
            return false;
        }

        if ((contextFile != null && that.contextFile == null) || (contextFile == null && that.contextFile != null)) {
            return false;
        }
        if ((pomFile != null && that.pomFile == null) || (pomFile == null && that.pomFile != null)) {
            return false;
        }

        if (contextFile != null && !contextFile.equals(that.contextFile)) {
            return false;
        }
        return pomFile == null || pomFile.getVersion().equals(that.pomFile.getVersion());
    }

    @Override
    public int hashCode() {
        int result = contextFile != null ? contextFile.hashCode() : 0;
        result = 31 * result + (pomFile != null ? pomFile.getVersion().hashCode() : 0);
        result = 31 * result + addonID.hashCode();
        return result;
    }

    @SneakyThrows
    public AddonContext(Path contextFile) {
        this.contextFile = contextFile;
        ZipFile zipFile = new ZipFile(contextFile.toString());
        JarInputStream jarStream = new JarInputStream(new FileInputStream(contextFile.toString()));
        this.manifest = jarStream.getManifest();
        this.pomFile = readPomFile(zipFile);
        jarStream.close();
        zipFile.close();
    }

    public AddonContext(String addonID) {
        this.addonID = addonID;
        this.contextFile = null;
        this.pomFile = null;
        this.manifest = null;
        this.internal = true;
    }

    public boolean isLoaded() {
        return config != null && loadError == null;
    }

    public Set<String> getDependencies() {
        return this.pomFile.getDependencies().stream()
                .filter(d -> d.getGroupId().equals("org.homio") && d.getArtifactId().contains("addon"))
                .map(Dependency::getArtifactId).collect(Collectors.toSet());
    }

    public String getAddonFriendlyName() {
        return Objects.toString(this.pomFile.getName(), addonID);
    }

    public AnnotationConfigApplicationContext getApplicationContext() {
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
            throw new ServerException("ERROR.ADDON_NO_VERSION" + addonID);
        }
        return version;
    }

    public static boolean validVersion(String addonVersion, int appVersion) {
        try {
            int addonMajor = Integer.parseInt(addonVersion.split("\\.")[0]);
            return addonMajor == appVersion;
        } catch (Exception ignore) {
        }
        return false;
    }

    public ClassLoader getClassLoader() {
        return HomioClassLoader.getAddonClassLoader(getAddonID());
    }

    public void onDestroy(ThrowingRunnable<Exception> destroyListener) {
        destryListeners.add(destroyListener);
    }

    @SneakyThrows
    public void load(ConfigurationBuilder configurationBuilder, Environment env, ApplicationContext parentContext,
                     ClassLoader classLoader) {
        URL addonUrl = contextFile.toUri().toURL();
        Reflections reflections = new Reflections(configurationBuilder.setUrls(addonUrl));

        config = new AddonSpringContext(env);
        addonID = pomFile.getArtifactId();
        try {
            HomioClassLoader.addClassLoaders(addonID, classLoader);
            config.configureSpringContext(reflections, parentContext, addonID, classLoader);
        } catch (Exception ex) {
            HomioClassLoader.removeClassLoader(addonID);
            loadError = CommonUtils.getErrorMessage(ex);
            throw ex;
        }
    }

    public void fireCloseListeners() {
        for (ThrowingRunnable<Exception> listener : destryListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                log.error("Error during fire addon {} destroy listener", addonID);
            }
        }
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

    @RequiredArgsConstructor
    public static class AddonSpringContext {

        private final Environment env;
        private AnnotationConfigApplicationContext ctx;
        private Class<?> configClass;

        void configureSpringContext(Reflections reflections, ApplicationContext parentContext, String addonID, ClassLoader classLoader) {
            configClass = findBatchConfigurationClass(reflections);
            AddonConfiguration addonConfiguration = configClass.getDeclaredAnnotation(AddonConfiguration.class);

            // create spring context
            ctx = new AnnotationConfigApplicationContext();
            ctx.setId(addonID);
            ctx.setParent(parentContext);
            ctx.setClassLoader(classLoader);
            ctx.setResourceLoader(new PathMatchingResourcePatternResolver(classLoader));

            // set custom environments
            Map<String, Object> customEnv = Stream.of(addonConfiguration.env()).collect(
                    Collectors.toMap(AddonConfiguration.Env::key, e ->
                            SpringUtils.replaceEnvValues(e.value(),
                                    (key, defValue, fullPrefix) -> env.getProperty(key, defValue))));

            if (!customEnv.isEmpty()) {
                ctx.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("AddonConfiguration PropertySource", customEnv));
            }

            // wake up spring context
            ctx.scan(configClass.getPackage().getName());
            ctx.register(configClass);

            ctx.refresh();
            ctx.start();
        }

        /**
         * Find spring configuration class with annotation @AddonConfiguration and @Configuration
         */
        private Class<?> findBatchConfigurationClass(Reflections reflections) {
            // find configuration class
            Set<Class<?>> springConfigClasses = reflections.getTypesAnnotatedWith(AddonConfiguration.class);
            if (springConfigClasses.isEmpty()) {
                throw new ServerException("ERROR.ADDON_NO_CONFIG");
            }
            if (springConfigClasses.size() > 1) {
                throw new ServerException("ERROR.ADDON_MANY_CONFIG", StringUtils.join(springConfigClasses, ", "));
            }
            Class<?> batchConfigurationClass = springConfigClasses.iterator().next();
            if (batchConfigurationClass.getDeclaredAnnotation(AddonConfiguration.class) == null) {
                throw new ServerException("ERROR.ADDON_UNABLE_LOAD_CONFIG");
            }
            return batchConfigurationClass;
        }
    }
}
