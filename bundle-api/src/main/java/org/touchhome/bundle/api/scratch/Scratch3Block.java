package org.touchhome.bundle.api.scratch;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;
import org.touchhome.bundle.api.EntityContext;

import javax.validation.constraints.NotNull;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

@Getter
public class Scratch3Block implements Comparable<Scratch3Block> {
    public static final String SUBSTACK = "SUBSTACK";
    public static final String CONDITION = "CONDITION";

    @JsonIgnore
    private int order;
    private String opcode;
    private BlockType blockType;
    private String text;
    private Map<String, ArgumentTypeDescription> arguments = new HashMap<>();
    @JsonIgnore
    private Scratch3BlockHandler handler;
    @JsonIgnore
    private Scratch3BlockEvaluateHandler evaluateHandler;
    @JsonIgnore
    private int spaceCount = 0;

    private Scratch3Color scratch3Color;

    private BiConsumer<String, WorkspaceBlock> allowLinkBoolean;
    private BiConsumer<String, WorkspaceBlock> allowLinkVariable;

    @JsonIgnore
    private LinkGeneratorHandler linkGenerator;

    protected Scratch3Block(int order, String opcode, BlockType blockType, String text, Scratch3BlockHandler handler, Scratch3BlockEvaluateHandler evaluateHandler) {
        this.order = order;
        this.opcode = opcode;
        this.blockType = blockType;
        this.text = text;
        this.handler = handler;
        this.evaluateHandler = evaluateHandler;
    }

    public static Scratch3Block ofHandler(int order, String opcode, BlockType blockType, String text, Scratch3BlockHandler handler) {
        return new Scratch3Block(order, opcode, blockType, text, handler, null);
    }

    @SneakyThrows
    public static <T extends Scratch3Block> T ofHandler(int order, String opcode, BlockType blockType, String text, Scratch3BlockHandler handler, Class<T> targetClass) {
        Constructor<T> constructor = targetClass.getDeclaredConstructor(int.class, String.class, BlockType.class, String.class, Scratch3BlockHandler.class, Scratch3BlockEvaluateHandler.class);
        return constructor.newInstance(order, opcode, blockType, text, handler, null);
    }

    public static Scratch3Block ofHandler(String opcode, BlockType blockType, Scratch3BlockHandler handler) {
        return new Scratch3Block(0, opcode, blockType, null, handler, null);
    }

    public static Scratch3Block ofEvaluate(int order, String opcode, BlockType blockType, String text, Scratch3BlockEvaluateHandler evalHandler) {
        return new Scratch3Block(order, opcode, blockType, text, null, evalHandler);
    }

    @SneakyThrows
    public static <T extends Scratch3Block> T ofEvaluate(int order, String opcode, BlockType blockType,
                                                         String text, Scratch3BlockEvaluateHandler evalHandler, Class<T> targetClass) {
        Constructor<T> constructor = targetClass.getDeclaredConstructor(int.class, String.class, BlockType.class, String.class, Scratch3BlockHandler.class, Scratch3BlockEvaluateHandler.class);
        return constructor.newInstance(order, opcode, blockType, text, null, evalHandler);
    }

    public static Scratch3Block ofEvaluate(String opcode, BlockType blockType, Scratch3BlockEvaluateHandler evalHandler) {
        return new Scratch3Block(0, opcode, blockType, null, null, evalHandler);
    }

    public ArgumentTypeDescription addArgument(String argumentName, ArgumentType type) {
        ArgumentTypeDescription argumentTypeDescription = new ArgumentTypeDescription(type, "", null);
        this.arguments.put(argumentName, argumentTypeDescription);
        return argumentTypeDescription;
    }

    public ArgumentTypeDescription addArgument(String argumentName, ArgumentType type, String defaultValue) {
        ArgumentTypeDescription argumentTypeDescription = new ArgumentTypeDescription(type, defaultValue, null);
        this.arguments.put(argumentName, argumentTypeDescription);
        return argumentTypeDescription;
    }

    public ArgumentTypeDescription addArgument(String argumentName, ArgumentType type, Object defaultValue, MenuBlock menu) {
        ArgumentTypeDescription argumentTypeDescription = new ArgumentTypeDescription(type, defaultValue == null ? null : defaultValue.toString(), menu);
        this.arguments.put(argumentName, argumentTypeDescription);
        return argumentTypeDescription;
    }

    public ArgumentTypeDescription addArgumentServerSelection(String argumentName, MenuBlock.ServerMenuBlock menu) {
        ArgumentTypeDescription argumentTypeDescription = new ArgumentTypeDescription(ArgumentType.string, menu.getItems().getFirstKV()[1], menu.getName(), menu);
        this.arguments.put(argumentName, argumentTypeDescription);
        return argumentTypeDescription;
    }

    @Override
    public int compareTo(@NotNull Scratch3Block o) {
        return Integer.compare(order, o.order);
    }

    public Pair<String, MenuBlock> findMenuBlock(Predicate<String> predicate) {
        for (String argument : arguments.keySet()) {
            if (predicate.test(argument)) {
                return Pair.of(argument, arguments.get(argument).getMenuBlock());
            }
        }
        return null;
    }

    public void appendSpace() {
        this.spaceCount++;
    }

    public void overrideColor(String color) {
        this.scratch3Color = new Scratch3Color(color);
    }

    public void allowLinkBoolean(BiConsumer<String, WorkspaceBlock> allowLinkBoolean) {
        this.allowLinkBoolean = allowLinkBoolean;
    }

    public void setLinkGenerator(LinkGeneratorHandler linkGenerator) {
        this.linkGenerator = linkGenerator;
    }

    public interface LinkGeneratorHandler {
        void handle(String varGroup, String varName, JSONObject parameter);
    }

    public void allowLinkFloatVariable(BiConsumer<String, WorkspaceBlock> allowLinkVariable) {
        this.allowLinkVariable = allowLinkVariable;
    }

    public WorkspaceCodeGenerator codeGenerator(String extension) {
        return new WorkspaceCodeGenerator(extension);
    }

    @FunctionalInterface
    public interface Scratch3BlockHandler {
        void handle(WorkspaceBlock workspaceBlock);
    }

    @FunctionalInterface
    public interface Scratch3BlockEvaluateHandler {
        Object handle(WorkspaceBlock workspaceBlock);
    }

    @Getter
    @RequiredArgsConstructor
    public static class ArgumentTypeDescription {
        private final ArgumentType type;
        private final String defaultValue;
        private final String menu;

        @JsonIgnore
        private final MenuBlock menuBlock;

        ArgumentTypeDescription(ArgumentType type, String defaultValue, MenuBlock menuBlock) {
            this.type = type;
            this.defaultValue = defaultValue;
            if (menuBlock != null) {
                this.menu = menuBlock.getName();
                this.menuBlock = menuBlock;
            } else {
                this.menu = null;
                this.menuBlock = null;
            }
        }
    }

    @RequiredArgsConstructor
    public class WorkspaceCodeGenerator {

        private final Map<String, Object> menuValues = new HashMap<>();
        private final String extension;

        public WorkspaceCodeGenerator setMenu(MenuBlock menuBlock, Object value) {
            menuValues.put(menuBlock.getName(), value);
            return this;
        }

        public void generateBooleanLink(String varGroup, String varName, EntityContext entityContext) {
            getLinkCodeGenerator(entityContext).generateBooleanLink(varGroup, varName);
        }

        public void generateFloatLink(String varGroup, String varName, EntityContext entityContext) {
            getLinkCodeGenerator(entityContext).generateFloatLink(varGroup, varName);
        }

        private LinkCodeGenerator getLinkCodeGenerator(EntityContext entityContext) {
            return new LinkCodeGenerator(extension, getOpcode(), entityContext, menuValues, getArguments());
        }
    }
}
