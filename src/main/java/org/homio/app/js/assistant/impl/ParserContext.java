package org.homio.app.js.assistant.impl;

import org.homio.api.model.UpdatableValue;

import java.util.function.Consumer;

public class ParserContext {

    private final ContextType contextType;

    private ParserContext(ContextType contextType) {
        this.contextType = contextType;
    }

    public static ParserContext noneContext() {
        return new ParserContext(ContextType.None);
    }

    public static TypeParserContext varTypeContext() {
        return new TypeParserContext();
    }

    public boolean is(ContextType contextType) {
        return this.contextType.equals(contextType);
    }

    public enum ContextType {
        VarEvaluate, None
    }

    public static class TypeParserContext extends ParserContext {

        public final UpdatableValue<Class> varType = UpdatableValue.wrap(Object.class, "");
        public final Consumer<Class> typeConsumer;

        private TypeParserContext() {
            super(ContextType.VarEvaluate);
            typeConsumer = aClass -> varType.update(aClass);
        }
    }
}
