package org.homio.app.workspace.block.core;

import lombok.Getter;
import org.homio.api.Context;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
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

    private final Random random = new SecureRandom();

    public Scratch3OperatorBlocks(Context context) {
        super("operator", context);

        // Blocks
        blockReporter("add", this::addEvaluateEvaluate);
        blockReporter("subtract", this::subtractEvaluateEvaluate);
        blockReporter("multiply", this::multiplyEvaluateEvaluate);
        blockReporter("divide", this::divideEvaluateEvaluate);
        blockReporter("random", this::randomEvaluateEvaluate);
        blockReporter("lt", this::ltEvaluateEvaluate);
        blockReporter("equals", this::equalsEvaluateEvaluate);
        blockReporter("gt", this::gtEvaluateEvaluate);
        blockReporter("and", this::andEvaluateEvaluate);
        blockReporter("or", this::orEvaluateEvaluate);
        blockReporter("not", this::notEvaluateEvaluate);
        blockReporter("mathop", this::mathOpEvaluateEvaluate);
        blockReporter("bool_to_num", this::boolToNumberEvaluate);
        blockReporter("bool_if_else", this::boolIfElseEvaluate);
    }

    private DecimalType boolIfElseEvaluate(WorkspaceBlock workspaceBlock) {
        String op = workspaceBlock.getInputBoolean("OPERAND") ? "THEN" : "ELSE";
        return new DecimalType(workspaceBlock.getInputFloat(op));
    }

    private OnOffType boolToNumberEvaluate(WorkspaceBlock workspaceBlock) {
        return OnOffType.of(workspaceBlock.getInputBoolean("OPERAND"));
    }

    private DecimalType mathOpEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        Float value = workspaceBlock.getInputFloat("NUM");
        return new DecimalType(mathOps.get(workspaceBlock.getField("OPERATOR")).apply(value));
    }

    private OnOffType notEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return OnOffType.of(
                !workspaceBlock.getInputWorkspaceBlock("OPERAND").evaluate().boolValue());
    }

    private OnOffType orEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return OnOffType.of(
                workspaceBlock.getInputWorkspaceBlock("OPERAND1").evaluate().boolValue()
                || workspaceBlock
                        .getInputWorkspaceBlock("OPERAND2")
                        .evaluate()
                        .boolValue());
    }

    private OnOffType andEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return OnOffType.of(
                workspaceBlock.getInputWorkspaceBlock("OPERAND1").evaluate().boolValue()
                && workspaceBlock
                        .getInputWorkspaceBlock("OPERAND2")
                        .evaluate()
                        .boolValue());
    }

    private OnOffType gtEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return OnOffType.of(
                Double.compare(
                        workspaceBlock.getInputFloat("OPERAND1"),
                        workspaceBlock.getInputFloat("OPERAND2"))
                > 0);
    }

    private OnOffType equalsEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return OnOffType.of(
                Double.compare(
                        workspaceBlock.getInputFloat("OPERAND1"),
                        workspaceBlock.getInputFloat("OPERAND2"))
                == 0);
    }

    private OnOffType ltEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return OnOffType.of(
                Double.compare(
                        workspaceBlock.getInputFloat("OPERAND1"),
                        workspaceBlock.getInputFloat("OPERAND2"))
                < 0);
    }

    private DecimalType randomEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        int min = workspaceBlock.getInputInteger("FROM");
        int max = workspaceBlock.getInputInteger("TO");
        return new DecimalType(random.nextInt(max - min + 1) + min);
    }

    private DecimalType divideEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return new DecimalType(
                workspaceBlock.getInputFloat("NUM1") / workspaceBlock.getInputFloat("NUM2"));
    }

    private DecimalType multiplyEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return new DecimalType(
                workspaceBlock.getInputFloat("NUM1") * workspaceBlock.getInputFloat("NUM2"));
    }

    private DecimalType subtractEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return new DecimalType(
                workspaceBlock.getInputFloat("NUM1") - workspaceBlock.getInputFloat("NUM2"));
    }

    private DecimalType addEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return new DecimalType(
                workspaceBlock.getInputFloat("NUM1") + workspaceBlock.getInputFloat("NUM2"));
    }
}
