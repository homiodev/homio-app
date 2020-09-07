package org.touchhome.app.workspace.block.core;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.scratch.BlockType;
import org.touchhome.bundle.api.scratch.Scratch3Block;
import org.touchhome.bundle.api.scratch.Scratch3ExtensionBlocks;
import org.touchhome.bundle.api.scratch.WorkspaceBlock;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

@Getter
@Component
public class Scratch3OperatorBlocks extends Scratch3ExtensionBlocks {

    private static Map<String, Function<Float, Number>> mathOps = new HashMap<>();

    static {
        mathOps.put("abs", Math::abs);
        mathOps.put("round", Math::round);
        mathOps.put("floor", Math::floor);
        mathOps.put("ceiling", Math::ceil);
        mathOps.put("sqrt", Math::sqrt);
        mathOps.put("sin", Math::sin);
        mathOps.put("cos", Math::cos);
        mathOps.put("tan", Math::tan);
        mathOps.put("asin", Math::asin);
        mathOps.put("acos", Math::acos);
        mathOps.put("atan", Math::atan);
        mathOps.put("log", Math::log);
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
    private Random random = new Random();

    public Scratch3OperatorBlocks(EntityContext entityContext) {
        super("operator", entityContext);

        // Blocks
        this.addBlock = Scratch3Block.ofEvaluate("add", BlockType.reporter, this::addEvaluateEvaluate);
        this.subtractBlock = Scratch3Block.ofEvaluate("subtract", BlockType.reporter, this::subtractEvaluateEvaluate);
        this.multiplyBlock = Scratch3Block.ofEvaluate("multiply", BlockType.reporter, this::multiplyEvaluateEvaluate);
        this.divideBlock = Scratch3Block.ofEvaluate("divide", BlockType.reporter, this::divideEvaluateEvaluate);
        this.randomBlock = Scratch3Block.ofEvaluate("random", BlockType.reporter, this::randomEvaluateEvaluate);
        this.ltBlock = Scratch3Block.ofEvaluate("lt", BlockType.reporter, this::ltEvaluateEvaluate);
        this.equalsBlock = Scratch3Block.ofEvaluate("equals", BlockType.reporter, this::equalsEvaluateEvaluate);
        this.gtBlock = Scratch3Block.ofEvaluate("gt", BlockType.reporter, this::gtEvaluateEvaluate);
        this.andBlock = Scratch3Block.ofEvaluate("and", BlockType.reporter, this::andEvaluateEvaluate);
        this.orBlock = Scratch3Block.ofEvaluate("or", BlockType.reporter, this::orEvaluateEvaluate);
        this.notBlock = Scratch3Block.ofEvaluate("not", BlockType.reporter, this::notEvaluateEvaluate);
        this.mathOpBlock = Scratch3Block.ofEvaluate("mathop", BlockType.reporter, this::mathOpEvaluateEvaluate);
        this.boolToNum = Scratch3Block.ofEvaluate("bool_to_num", BlockType.reporter, this::boolToNumberEvaluate);

        this.postConstruct();
    }

    private Object boolToNumberEvaluate(WorkspaceBlock workspaceBlock) {
        return workspaceBlock.getInputBoolean("OPERAND");
    }

    private Number mathOpEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        Float value = workspaceBlock.getInputFloat("NUM");
        return mathOps.get(workspaceBlock.getField("OPERATOR")).apply(value);
    }

    private boolean notEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return !((boolean) workspaceBlock.getInputWorkspaceBlock("OPERAND1").evaluate());
    }

    private boolean orEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return ((boolean) workspaceBlock.getInputWorkspaceBlock("OPERAND1").evaluate()) || ((boolean) workspaceBlock.getInputWorkspaceBlock("OPERAND2").evaluate());
    }

    private boolean andEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return ((boolean) workspaceBlock.getInputWorkspaceBlock("OPERAND1").evaluate()) && ((boolean) workspaceBlock.getInputWorkspaceBlock("OPERAND2").evaluate());
    }

    private boolean gtEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return Double.compare(workspaceBlock.getInputFloat("OPERAND1"), workspaceBlock.getInputFloat("OPERAND2")) > 0;
    }

    private boolean equalsEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return Double.compare(workspaceBlock.getInputFloat("OPERAND1"), workspaceBlock.getInputFloat("OPERAND2")) == 0;
    }

    private boolean ltEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return Double.compare(workspaceBlock.getInputFloat("OPERAND1"), workspaceBlock.getInputFloat("OPERAND2")) < 0;
    }

    private int randomEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        int min = workspaceBlock.getInputInteger("FROM");
        int max = workspaceBlock.getInputInteger("TO");
        return random.nextInt(max - min + 1) + min;
    }

    private double divideEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return workspaceBlock.getInputFloat("NUM1") / workspaceBlock.getInputFloat("NUM2");
    }

    private double multiplyEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return workspaceBlock.getInputFloat("NUM1") * workspaceBlock.getInputFloat("NUM2");
    }

    private double subtractEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return workspaceBlock.getInputFloat("NUM1") - workspaceBlock.getInputFloat("NUM2");
    }

    private double addEvaluateEvaluate(WorkspaceBlock workspaceBlock) {
        return workspaceBlock.getInputFloat("NUM1") + workspaceBlock.getInputFloat("NUM2");
    }
}
