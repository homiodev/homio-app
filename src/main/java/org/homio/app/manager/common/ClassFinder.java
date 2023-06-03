package org.homio.app.manager.common;

import static org.homio.app.manager.CacheService.JS_COMPLETIONS;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.entity.BaseEntity;
import org.homio.api.exception.ServerException;
import org.homio.api.util.CommonUtils;
import org.homio.app.HomioClassLoader;
import org.homio.app.repository.AbstractRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClassFinder {

    public static final String CLASSES_WITH_PARENT_CLASS = "CLASSES_WITH_PARENT_CLASS";
    public static final String REPOSITORY_BY_CLAZZ = "REPOSITORY_BY_CLAZZ";

    @SneakyThrows
    public static <T> List<T> createClassesWithParent(Class<T> parentClass, ClassFinder classFinder) {
        List<T> list = new ArrayList<>();
        for (Class<? extends T> clazz : classFinder.getClassesWithParent(parentClass)) {
            list.add(CommonUtils.newInstance(clazz));
        }
        return list;
    }

    public static List<Class<?>> findAllParentClasses(Class<?> childClass, Class<?> topClass) {
        List<Class<?>> result = new ArrayList<>();
        if (!topClass.isAssignableFrom(childClass)) {
            throw new RuntimeException(
                "Class <" + childClass.getSimpleName() + "> isn't assigned of class <" + topClass.getSimpleName() + ">");
        }
        while (!childClass.getSimpleName().equals(topClass.getSimpleName())) {
            result.add(childClass);
            childClass = childClass.getSuperclass();
        }
        return result;
    }

    /*public static <T extends Annotation> List<Pair<Class, List<T>>> findAllAnnotationsToParentAnnotation(Class typeClass,
        Class<T> aAnnotation,
        Class<? extends Annotation> bAnnotation) {
        Class cursor = typeClass;
        List<Pair<Class, List<T>>> typeToAnnotations = new ArrayList<>();
        while (cursor != null) {
            T[] annotations = (T[]) typeClass.getDeclaredAnnotationsByType(aAnnotation);
            if (annotations.length > 0) {
                typeToAnnotations.add(Pair.of(cursor, Arrays.asList(annotations)));
            }
            // we need allow to handle class with annotation bAnnotation, that's why this not in while block
            if (cursor.isAnnotationPresent(bAnnotation)) {
                break;
            }
            cursor = cursor.getSuperclass();
        }
        return typeToAnnotations;
    }*/

    public <T> List<Class<? extends T>> getClassesWithParent(
        @NotNull Class<T> parentClass,
        @Nullable String basePackage,
        @Nullable ClassLoader classLoader) {

        List<Class<? extends T>> foundClasses = new ArrayList<>();
        ClassPathScanningCandidateComponentProvider scanner = HomioClassLoader.getResourceScanner(false, classLoader);
        scanner.addIncludeFilter(new AssignableTypeFilter(parentClass));

        getClassesWithParentFromPackage(StringUtils.defaultString(basePackage, "org.homio"), null, scanner,
            foundClasses);

        if (foundClasses.isEmpty() && basePackage == null) {
            getClassesWithParentFromPackage("com.pi4j", null, scanner, foundClasses);
        }

        return foundClasses;
    }

    /**
     * NOT call this method from this class
     */
    @Cacheable(CLASSES_WITH_PARENT_CLASS)
    public <T> List<Class<? extends T>> getClassesWithParent(Class<T> parentClass) {
        return getClassesWithParent(parentClass, null, null);
    }

    @Cacheable(JS_COMPLETIONS)
    public <T> List<Class<? extends T>> getClassesWithParentSpecific(@NotNull Class<T> parentClass, String className, String basePackage) {
        List<Class<? extends T>> foundClasses = new ArrayList<>();
        ClassPathScanningCandidateComponentProvider scanner = HomioClassLoader.getResourceScanner(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(parentClass));

        getClassesWithParentFromPackage(StringUtils.defaultString(basePackage, "org.homio"), className, scanner,
            foundClasses);

        if (foundClasses.isEmpty() && basePackage == null) {
            getClassesWithParentFromPackage("com.pi4j", className, scanner, foundClasses);
        }

        return foundClasses;
    }

    @Cacheable(REPOSITORY_BY_CLAZZ)
    public <T extends BaseEntity, R extends AbstractRepository<T>> R getRepositoryByClass(Class<T> clazz) {
        List<R> potentialRepository = new ArrayList<>();

        for (AbstractRepository abstractRepository : EntityContextImpl.repositories.values()) {
            if (abstractRepository.getEntityClass().equals(clazz)) {
                return (R) abstractRepository;
            }
            if (abstractRepository.getEntityClass().isAssignableFrom(clazz)) {
                potentialRepository.add((R) abstractRepository);
            }
        }
        if (!potentialRepository.isEmpty()) {
            if (potentialRepository.size() == 1) {
                return potentialRepository.get(0);
            }
            // find most child repository
            R bestPotentialRepository = null;
            int lowestLevel = 100;
            for (R r : potentialRepository) {
                Class entityClass = clazz;
                // get level
                int level = 0;
                while (entityClass != null) {
                    if (entityClass.equals(r.getEntityClass())) {
                        if (lowestLevel > level) {
                            lowestLevel = level;
                            bestPotentialRepository = r;
                        }
                        break;
                    } else {
                        level++;
                        entityClass = entityClass.getSuperclass();
                    }
                }
            }
            if (bestPotentialRepository != null) {
                return bestPotentialRepository;
            }
        }

        throw new ServerException("Unable find repository for entity class: " + clazz);
    }

    public  <T> List<Class<? extends T>> getClassesWithAnnotation(Class<? extends Annotation> annotation) {
        List<Class<? extends T>> foundClasses = new ArrayList<>();
        ClassPathScanningCandidateComponentProvider scanner = HomioClassLoader.getResourceScanner(true);
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
        for (BeanDefinition bd : scanner.findCandidateComponents("org.homio")) {
            try {
                foundClasses.add((Class<? extends T>) scanner.getResourceLoader().getClassLoader().loadClass(bd.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new ServerException(e);
            }
        }
        return foundClasses;
    }

    private <T> void getClassesWithParentFromPackage(String basePackage, String className,
        ClassPathScanningCandidateComponentProvider scanner,
        List<Class<? extends T>> foundClasses) {
        try {
            for (BeanDefinition bd : scanner.findCandidateComponents(basePackage)) {
                if (className == null || bd.getBeanClassName().endsWith("." + className)) {
                    try {
                        foundClasses.add((Class<? extends T>) scanner.getResourceLoader().getClassLoader()
                                                                     .loadClass(bd.getBeanClassName()));
                    } catch (ClassNotFoundException ignore) {
                    }
                }
            }
        } catch (Exception ignore) {
        }
    }
}
