package org.homio.app.utils;

import java.lang.reflect.Method;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.tika.Tika;

public final class InternalUtil {

    public static final double GB_DIVIDER = 1024 * 1024 * 1024;
    public static final Tika TIKA = new Tika();

    public static Method findMethodByName(Class clz, String name) {
        String capitalizeName = StringUtils.capitalize(name);
        Method method = MethodUtils.getAccessibleMethod(clz, "get" + capitalizeName);
        if (method == null) {
            method = MethodUtils.getAccessibleMethod(clz, "is" + capitalizeName);
        }
        return method;
    }

    public static String getMethodShortName(Method method) {
        return StringUtils.uncapitalize(method.getName().substring(method.getName().startsWith("is") ? 2 : 3));
    }
}
