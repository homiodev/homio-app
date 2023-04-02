package org.homio.app.js.assistant.impl;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

final class MethodParser {

    static Set<Method> getFitMethods(String finalNext, Class clazz) {
        MethodFitParser methodFitParser = MethodFitParser.getMethodFitParser(finalNext);
        String methodName = methodFitParser.getMethodName(finalNext);

        Set<Method> methods = new HashSet<>();
        Stream<Method> stream = clazz.getSuperclass() != null ?
            Stream.concat(Stream.of(clazz.getMethods()), Stream.of(clazz.getSuperclass().getMethods())) :
            Stream.of(clazz.getMethods());

        stream.forEach(method -> {
            if (!method.getDeclaringClass().getName().startsWith("org.homio.smart.") &&
                !method.getDeclaringClass().getName().equals("java.lang.Object")
                && !method.getDeclaringClass().getName().equals("java.lang.Enum")) {
                if (methodFitParser.match(method, methodName)) {
                    methods.add(method);
                }
            }
        });
        return methods;
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
