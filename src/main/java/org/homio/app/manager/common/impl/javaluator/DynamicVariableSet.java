package org.homio.app.manager.common.impl.javaluator;

import com.fathzer.soft.javaluator.StaticVariableSet;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.homio.app.manager.common.impl.EntityContextVarImpl;

@RequiredArgsConstructor
public class DynamicVariableSet extends StaticVariableSet<Object> {

    private final List<String> sources;
    private final EntityContextVarImpl var;

    @Override
    public Object get(String variableName) {
        Object value = super.get(variableName);
        if (value != null) {
            return value;
        }
        if (variableName.startsWith("VAR")) {
            int index = Integer.parseInt(variableName.substring("VAR".length()));
            Number number = (Number) var.get(sources.get(index));
            value = number == null ? 0D : number.doubleValue();
            set(variableName, value);
        } else if (variableName.startsWith("'VAR")) {
            int index = Integer.parseInt(variableName.substring("'VAR".length(), variableName.length() - 1));
            return sources.get(index);
        }
        // string case
        /*if (variableName.charAt(0) == '\'') {
            return variableName.substring(1, variableName.length() - 2);
        }*/
        if (variableName.startsWith("PT")) {
            return (double) Duration.parse(variableName).getSeconds();
        }
        return value;
    }
}
