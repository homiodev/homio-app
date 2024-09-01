package org.homio.app.manager.common.impl.javaluator;

import com.fathzer.soft.javaluator.AbstractVariableSet;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.exception.ServerException;
import org.homio.app.manager.common.impl.ContextVarImpl.TransformVariableSourceImpl;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

@RequiredArgsConstructor
public class DynamicVariableSet implements AbstractVariableSet<Object> {

    private final @Getter
    @NotNull List<TransformVariableSourceImpl> sources;

    @Override
    public Object get(String variableName) {
        if (variableName.startsWith("VAR")) {
            int index = Integer.parseInt(variableName.substring("VAR".length()));
            assertVarExists(index);
            Number number = sources.get(index).getHandler().getValue();
            return number == null ? 0D : number.doubleValue();
        } else if (variableName.startsWith("'VAR")) {
            int index = Integer.parseInt(variableName.substring("'VAR".length(), variableName.length() - 1));
            assertVarExists(index);
            return sources.get(index).getListenSource();
        }
        // string case
        /* if (variableName.charAt(0) == '\'') {
            return variableName.substring(1, variableName.length() - 2);
        } */
        if (variableName.startsWith("PT")) {
            return (double) Duration.parse(variableName).getSeconds();
        }
        return null;
    }

    private void assertVarExists(int index) {
        if (sources.size() <= index) {
            throw new ServerException("Unable to find 'VAR%s'".formatted(index));
        }
    }
}
