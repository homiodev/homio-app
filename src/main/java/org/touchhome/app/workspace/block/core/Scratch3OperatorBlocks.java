package org.touchhome.app.workspace.block.core;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.state.DecimalType;
import org.touchhome.bundle.api.state.OnOffType;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.Scratch3Block;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

@Getter
@Component
public class Scratch3OperatorBlocks extends Scratch3ExtensionBlocks {

    private static final Map<String, Function<Float, BigDecimal>> mathOps = new HashMap<>();

    static {
        mathOps.put("abs", a -> BigDecimal.valueOf(Math.abs(a)));
        mathOps.put("round", a -> BigDecimal.valueOf(Math.round(a)));
        mathOps.put("floor", a -> BigDecimal.valueOf(Math.floor(a)));
        mathOps.put("ceiling", a -> BigDecimal.valueOf(Math.ceil(a)));
        mathOps.put("sqrt", a -> BigDecimal.valueOf(Math.sqrt(a)));
        mathOps.put("sin", a -> BigDecimal.valueOf(Math.sin(a)));
        mathOps.put("cos", a -> BigDecimal.valueOf(Math.cos(a)));
        mathOps.put("tan", a -> BigDecimal.valueOf(Math.tan(a)));
        mathOps.put("asin", a -> BigDecimal.valueOf(Math.asin(a)));
        mathOps.put("acos", a -> BigDecimal.valueOf(Math.acos(a)));
        mathOps.put("atan", a -> BigDecimal.valueOf(Math.atan(a)));
        mathOps.put("log", a -> BigDecimal.valueOf(Math.log(a)));
    }

    private final Scratch3Block addBlock;
    private final Scratch3Block subtractBlock;
    private final Scratch3Block multiplyBlock;
    private final Scratch3Block divideBlock;
    private final Scratch3Block randomBlock;
    private final Scratch3Block ltBlock;
    private final Scratch3Block equalsBlock;
    private final Scratch3Block gtBlock;
    private final Scratch3Block andBlock;
    private final Scratch3Block orBlock;
    private final Scratch3Block notBlock;
    private final Scratch3Block mathOpBlock;
    private final Scratch3Block boolToNum;
    private final Random random = new Random();

    public Scratch3OperatorBlocks(EntityContext entityContext) {
        super("operator", entityContext);

        // Blocks
        this.addBlock = Scratch3Block.ofReporter("add", this::addEvaluateEvaluate);
        this.subtractBlock = Scratch3Block.ofReporter("subtract", this::subtractEvaluateEvaluate);
        this.multiplyBlock = Scratch3Block.ofReporter("multiply", this::multiplyEvaluateEvaluate);
        this.divideBlock = Scratch3Block.ofReporter("divide", this::divideEvaluateEvaluate);
        this.randomBlock = Scratch3Block.ofReporter("random", this::randomEvaluateEvaluate);
        this.ltBlock = Scratch3Block.ofReporter("lt", this::ltEvaluateEvaluate);
        this.equalsBlock = Scratch3Block.ofReporter("equals", this::equalsEvaluateEvaluate);
        this.gtBlock = Scratch3Block.ofReporter("gt", this::gtEvaluateEvaluate);
        this.andBlock = Scratch3Block.ofReporter("and", this::andEvaluateEvaluate);
        this.orBlock = Scratch3Block.ofReporter("or", this::orEvaluateEvaluate);
        this.notBlock = Scratch3Block.ofReporter("not", this::notEvaluateEvaluate);
        this.mathOpBlock = Scratch3Block.ofReporter("mathop", this::mathOpEvaluateEvaluate);
        this.boolToNum = Scratch3Block.ofReporter("bool_to_num", this::boolToNumberEvaluate);
    }

    private OnOffType boolToNumberEvaluate(WorkspaceBlock workspaceBlock) {
        return OnOffType.of(workspaceBlock.getInputBoolean("OPERAND"));
    }

    private DecimalType mathOpEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        Float value = workspaceBlock.getInputFloat("NUM");
        return new DecimalType(mathOps.get(workspaceBlock.getField("OPERATOR")).apply(value));
    }

    private OnOffType notEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return OnOffType.of(!workspaceBlock.getInputWorkspaceBlock("OPERAND1").evaluate().boolValue());
    }

    private OnOffType orEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return OnOffType.of(workspaceBlock.getInputWorkspaceBlock("OPERAND1").evaluate().boolValue() ||
                workspaceBlock.getInputWorkspaceBlock("OPERAND2").evaluate().boolValue());
    }

    private OnOffType andEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return OnOffType.of(workspaceBlock.getInputWorkspaceBlock("OPERAND1").evaluate().boolValue() &&
                workspaceBlock.getInputWorkspaceBlock("OPERAND2").evaluate().boolValue());
    }

    private OnOffType gtEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return OnOffType.of(
                Double.compare(workspaceBlock.getInputFloat("OPERAND1"), workspaceBlock.getInputFloat("OPERAND2")) > 0);
    }

    private OnOffType equalsEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return OnOffType.of(
                Double.compare(workspaceBlock.getInputFloat("OPERAND1"), workspaceBlock.getInputFloat("OPERAND2")) == 0);
    }

    private OnOffType ltEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return OnOffType.of(
                Double.compare(workspaceBlock.getInputFloat("OPERAND1"), workspaceBlock.getInputFloat("OPERAND2")) < 0);
    }

    private DecimalType randomEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        int min = workspaceBlock.getInputInteger("FROM");
        int max = workspaceBlock.getInputInteger("TO");
        return new DecimalType(random.nextInt(max - min + 1) + min);
    }

    private DecimalType divideEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return new DecimalType(workspaceBlock.getInputFloat("NUM1") / workspaceBlock.getInputFloat("NUM2"));
    }

    private DecimalType multiplyEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return new DecimalType(workspaceBlock.getInputFloat("NUM1") * workspaceBlock.getInputFloat("NUM2"));
    }

    private DecimalType subtractEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return new DecimalType(workspaceBlock.getInputFloat("NUM1") - workspaceBlock.getInputFloat("NUM2"));
    }

    private DecimalType addEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return new DecimalType(workspaceBlock.getInputFloat("NUM1") + workspaceBlock.getInputFloat("NUM2"));
    }
}
