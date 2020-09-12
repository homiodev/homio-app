package org.touchhome.app.utils;

import java.beans.Introspector;
import java.lang.reflect.Method;

public final class InternalUtil {
    public static String getMethodShortName(Method method) {
        return Introspector.decapitalize(method.getName().substring(method.getName().startsWith("is") ? 2 : 3));
    }
}
