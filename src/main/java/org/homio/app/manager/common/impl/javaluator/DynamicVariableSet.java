package org.homio.app.manager.common.impl.javaluator;

import com.fathzer.soft.javaluator.AbstractVariableSet;
import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.exception.ServerException;
import org.homio.app.manager.common.impl.EntityContextVarImpl;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class DynamicVariableSet implements AbstractVariableSet<Object> {

    private final @Getter @NotNull List<String> sources;
    private final EntityContextVarImpl var;

    @Override
    public Object get(String variableName) {
        if (variableName.startsWith("VAR")) {
            int index = Integer.parseInt(variableName.substring("VAR".length()));
            assertVarExists(index);
            Number number = (Number) var.get(sources.get(index));
            return number == null ? 0D : number.doubleValue();
        } else if (variableName.startsWith("'VAR")) {
            int index = Integer.parseInt(variableName.substring("'VAR".length(), variableName.length() - 1));
            assertVarExists(index);
            return sources.get(index);
        }
        // string case
        /*if (variableName.charAt(0) == '\'') {
            return variableName.substring(1, variableName.length() - 2);
        }*/
        if (variableName.startsWith("PT")) {
            return (double) Duration.parse(variableName).getSeconds();
        }
        throw new IllegalArgumentException("Unable to find variable: " + variableName);
    }

    private void assertVarExists(int index) {
        if (sources.size() <= index) {
            throw new ServerException("Unable to find 'VAR%s'".formatted(index));
        }
    }
}
