package org.homio.app.js.assistant.impl;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class MethodParser {

    public static Method getFitStaticMethod(String finalNext, Class clazz) {
        return getFitMethod(Stream.of(clazz.getMethods()), finalNext, method -> Modifier.isStatic(method.getModifiers()));
    }

    public static Method getFitMethod(String finalNext, Class clazz) {
        Stream<Method> stream = clazz.getSuperclass() != null ?
                Stream.concat(Stream.of(clazz.getMethods()), Stream.of(clazz.getSuperclass().getMethods())) :
                Stream.of(clazz.getMethods());

        return getFitMethod(stream, finalNext, method -> true);
    }

    static Set<Method> getFitMethods(String finalNext, Class clazz) {
        MethodFitParser methodFitParser = MethodFitParser.getMethodFitParser(finalNext);
        String methodName = methodFitParser.getMethodName(finalNext);

        Set<Method> methods = new HashSet<>();
        Stream<Method> stream = clazz.getSuperclass() != null ?
                Stream.concat(Stream.of(clazz.getMethods()), Stream.of(clazz.getSuperclass().getMethods())) :
                Stream.of(clazz.getMethods());

        stream.forEach(method -> {
            if (!method.getDeclaringClass().getName().startsWith("org.homio.") &&
                    !method.getDeclaringClass().getName().equals("java.lang.Object")
                    && !method.getDeclaringClass().getName().equals("java.lang.Enum")) {
                if (methodFitParser.match(method, methodName)) {
                    methods.add(method);
                }
            }
        });
        return methods;
    }

    private static Method getFitMethod(Stream<Method> stream, String finalNext, Predicate<Method> testPredicate) {
        final List<Method> fitMethods = new ArrayList<>();
        final Method[] anyMethod = new Method[1];
        List<Class<?>> params = Collections.emptyList();
        int openCurveIndex = finalNext.indexOf("(");
        final String methodPrefix = openCurveIndex > 0 ? finalNext.substring(0, openCurveIndex) : finalNext;
        if (openCurveIndex > 0) {
            int closeCurveIndex = finalNext.lastIndexOf(")");
            if (closeCurveIndex > 0) {
                String parameters = finalNext.substring(openCurveIndex + 1, closeCurveIndex);
                params = evalMethodParameters(parameters);
            }
        }

        stream.filter(testPredicate).forEach(method -> {
            anyMethod[0] = method;
            if (method.getName().equals(methodPrefix)) {
                fitMethods.add(method);
            }
        });
        if (!params.isEmpty()) {
            for (Method method : fitMethods) {
                if (isMethodFitToParameters(method, params)) {
                    return method;
                }
            }
        }
        return fitMethods.isEmpty() ? anyMethod[0] : fitMethods.get(0);
    }

    private static boolean isMethodFitToParameters(Method method, List<Class<?>> params) {
        Parameter[] parameters = method.getParameters();
        if (params.size() >= parameters.length) {
            for (int i = 0; i < params.size(); i++) {
                Class<?> paramType = params.get(i);
                if (!paramType.isAssignableFrom(parameters[i].getType())) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    private static List<Class<?>> evalMethodParameters(String methodParameters) {
        List<Class<?>> params = new ArrayList<>();
        for (String param : methodParameters.trim().split(",", -1)) {
            String trimParam = param.trim();
            if (trimParam.startsWith("\"")) {
                params.add(String.class);
            } else {
                if (StringUtils.isNumeric(trimParam)) {
                    // who know. app not for parsing at all
                    try {
                        Integer.parseInt(trimParam);
                        params.add(Integer.class);
                    } catch (Exception ignoreI) {
                        try {
                            Long.parseLong(trimParam);
                            params.add(Long.class);
                        } catch (Exception ignoreL) {
                            try {
                                Double.parseDouble(trimParam);
                                params.add(Double.class);
                            } catch (Exception ignoreD) {
                                params.add(Object.class);
                            }
                        }
                    }
                } else {
                    params.add(Object.class);
                }
            }
        }
        return params;
    }

    private enum MethodFitParser {
        START_WITH {
            @Override
            public boolean match(Method method, String methodName) {
                return method.getName().startsWith(methodName);
            }
        }, EQUAL {
            @Override
            public boolean match(Method method, String methodName) {
                return method.getName().equals(methodName);
            }
        };

        public static MethodFitParser getMethodFitParser(String finalNext) {
            return finalNext.indexOf("(") > 0 ? EQUAL : START_WITH;
        }

        public String getMethodName(String finalNext) {
            return finalNext.indexOf("(") > 0 ? finalNext.substring(0, finalNext.indexOf("(")) : finalNext;
        }

        public abstract boolean match(Method method, String methodName);
    }
}
