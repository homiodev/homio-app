package org.touchhome.bundle.api.scratch;

public enum BlockType {
    /**
     * Boolean reporter with hexagonal shape
     */
    Boolean,

    /**
     * A button (not an actual block) for some special action, like making a variable
     */
    button,

    /**
     * Command block
     */
    command,

    /**
     * Specialized command block which may or may not run a child branch
     * The thread continues with the next block whether or not a child branch ran.
     */
    conditional,

    /**
     * Specialized hat block with no implementation function
     * This stack only runs if the corresponding event is emitted by other code.
     */
    event,

    /**
     * Hat block which conditionally starts a block stack
     */
    hat,

    hat_event,

    /**
     * Specialized command block which may or may not run a child branch
     * If a child branch runs, the thread evaluates the loop block again.
     */
    loop,

    /**
     * General reporter with numeric or string value
     */
    reporter
}
