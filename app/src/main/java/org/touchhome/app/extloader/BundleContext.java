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
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.touchhome.bundle.api.BundleConfiguration;
import org.touchhome.bundle.api.util.SpringUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
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
    private final String bundleName;

    private Path baseDir;
    private boolean loaded;
    private boolean internal;
    private boolean installed;
    private AnnotationConfigApplicationContext applicationContext;
    private BundleClassLoader bundleClassLoader;

    @SneakyThrows
    BundleContext(Path bundleContextFile) {
        this.bundleContextFile = bundleContextFile;
        this.pomFile = readPomFile(new ZipFile(bundleContextFile.toString()));
        this.bundleName = pomFile.getArtifactId();
    }

    BundleContext(String bundleName) {
        this.bundleName = bundleName;
        this.bundleContextFile = null;
        this.pomFile = null;
        this.internal = true;
    }

    /**
     * Initialize and return baseDir for context (may share same base dir for multiple jars)
     */
    Path getBaseDir() throws IOException {
        if (baseDir == null) {
            baseDir = ExtUtil.unpack(bundleContextFile);
        }
        return baseDir;

    }

    private Model readPomFile(ZipFile file) throws IOException, XmlPullParserException {
        for (ZipEntry e : Collections.list(file.entries())) {
            if (e.getName().endsWith("/pom.xml")) {
                return pomReader.read(file.getInputStream(e));
            }
        }
        throw new RuntimeException("Unable to find pom.xml in jar");
    }

    public Set<String> getDependencies() {
        return this.pomFile.getDependencies().stream().filter(d -> d.getGroupId().equals("org.touchhome"))
                .map(Dependency::getArtifactId).collect(Collectors.toSet());
    }

    void load(ConfigurationBuilder configurationBuilder, Environment env, BundleClassLoader bundleClassLoader, ApplicationContext parentContext) throws MalformedURLException {
        Reflections reflections = new Reflections(configurationBuilder.setUrls(baseDir.resolve(bundleContextFile).toUri().toURL()));

        BundleSpringContext config = new BundleSpringContext(env);
        config.configure(reflections, bundleClassLoader, null/*batchProgramEventPublisher*/, parentContext);
        ExtUtil.addToClasspath(bundleContextFile.toUri().toURL());
        this.bundleClassLoader = bundleClassLoader;
        this.applicationContext = config.getContext();
        this.loaded = true;
    }

    @RequiredArgsConstructor
    private static class BundleSpringContext {

        private AnnotationConfigApplicationContext ctx;
        private final Environment env;

        void configure(Reflections reflections, BundleClassLoader bundleClassLoader, BeanPostProcessor beanPostProcessor, ApplicationContext parentContext) {
            Class<?> configClass = findBatchConfigurationClass(reflections);
            BundleConfiguration bundleConfiguration = configClass.getDeclaredAnnotation(BundleConfiguration.class);

            // create spring context
            ctx = new AnnotationConfigApplicationContext();
            ctx.setParent(parentContext);
            ctx.setClassLoader(bundleClassLoader);
            ctx.setResourceLoader(new PathMatchingResourcePatternResolver(bundleClassLoader));

            // set custom environments
            Map<String, Object> customEnv = Stream.of(bundleConfiguration.env()).collect(
                    Collectors.toMap(BundleConfiguration.Env::key, e -> SpringUtils.replaceEnvValues(e.value(), env::getProperty)));

            if (!customEnv.isEmpty()) {
                ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("BundleConfiguration PropertySource", customEnv));
            }

            // wake up spring context
            ctx.scan(configClass.getPackage().getName());
            ctx.register(configClass);
            if (beanPostProcessor != null) {
                ctx.addBeanFactoryPostProcessor(beanFactory -> beanFactory.addBeanPostProcessor(beanPostProcessor));
            }
            bundleClassLoader.prepareForSpring();
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
                throw new RuntimeException("Configuration class with annotation @BundleConfiguration not found. Not possible to create spring context");
            }
            if (springConfigClasses.size() > 1) {
                throw new RuntimeException("Configuration class with annotation @BundleConfiguration must be unique, but found: " + StringUtils.join(springConfigClasses, ", "));
            }
            Class<?> batchConfigurationClass = springConfigClasses.iterator().next();
            if (batchConfigurationClass.getDeclaredAnnotation(Configuration.class) == null) {
                throw new RuntimeException("Configuration class with annotation @BundleConfiguration must have @Configuration annotation");
            }
            if (batchConfigurationClass.getDeclaredAnnotation(BundleConfiguration.class) == null) {
                throw new RuntimeException("Loaded batch definition has different ws-service-api.jar version and can not be instantiated");
            }
            return batchConfigurationClass;
        }

        public AnnotationConfigApplicationContext getContext() {
            return ctx;
        }
    }
}
