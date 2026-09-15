package org.ruby_lang.prism;

import java.util.Arrays;

// Keep in sync with Ruby MarkNewlinesVisitor
final class MarkNewlinesVisitor extends AbstractNodeVisitor<Void> {

    private final Nodes.Source source;
    private boolean[] newlineMarked;

    MarkNewlinesVisitor(Nodes.Source source) {
        this.source = source;
        this.newlineMarked = new boolean[1 + source.getLineCount()];
    }

    @Override
    public Void visitBlockNode(Nodes.BlockNode node) {
        boolean[] oldNewlineMarked = this.newlineMarked;
        this.newlineMarked = new boolean[oldNewlineMarked.length];
        try {
            return super.visitBlockNode(node);
        } finally {
            this.newlineMarked = oldNewlineMarked;
        }
    }

    @Override
    public Void visitLambdaNode(Nodes.LambdaNode node) {
        boolean[] oldNewlineMarked = this.newlineMarked;
        this.newlineMarked = new boolean[oldNewlineMarked.length];
        try {
            return super.visitLambdaNode(node);
        } finally {
            this.newlineMarked = oldNewlineMarked;
        }
    }

    @Override
    public Void visitDefNode(Nodes.DefNode node) {
        boolean[] oldNewlineMarked = this.newlineMarked;
        this.newlineMarked = new boolean[oldNewlineMarked.length];
        // The body of an endless method definition never emits newline events,
        // so in that case mark every line as already seen instead. There is no
        // location for the `=` operator here, but only an endless method
        // definition has a statements body which ends with the def node itself.
        if (node.body instanceof Nodes.StatementsNode && node.endOffset() == node.body.endOffset()) {
            Arrays.fill(this.newlineMarked, true);
        }
        try {
            return super.visitDefNode(node);
        } finally {
            this.newlineMarked = oldNewlineMarked;
        }
    }

    @Override
    public Void visitClassNode(Nodes.ClassNode node) {
        boolean[] oldNewlineMarked = this.newlineMarked;
        this.newlineMarked = new boolean[oldNewlineMarked.length];
        try {
            return super.visitClassNode(node);
        } finally {
            this.newlineMarked = oldNewlineMarked;
        }
    }

    @Override
    public Void visitModuleNode(Nodes.ModuleNode node) {
        boolean[] oldNewlineMarked = this.newlineMarked;
        this.newlineMarked = new boolean[oldNewlineMarked.length];
        try {
            return super.visitModuleNode(node);
        } finally {
            this.newlineMarked = oldNewlineMarked;
        }
    }

    @Override
    public Void visitSingletonClassNode(Nodes.SingletonClassNode node) {
        boolean[] oldNewlineMarked = this.newlineMarked;
        this.newlineMarked = new boolean[oldNewlineMarked.length];
        try {
            return super.visitSingletonClassNode(node);
        } finally {
            this.newlineMarked = oldNewlineMarked;
        }
    }

    // Statements inside string interpolation do not emit newline events, so
    // mark every line as already seen while visiting them. Nested scopes
    // (blocks, lambdas, defs, etc.) reset the lines and emit events again.
    @Override
    public Void visitEmbeddedStatementsNode(Nodes.EmbeddedStatementsNode node) {
        boolean[] oldNewlineMarked = this.newlineMarked;
        this.newlineMarked = new boolean[oldNewlineMarked.length];
        Arrays.fill(this.newlineMarked, true);
        try {
            return super.visitEmbeddedStatementsNode(node);
        } finally {
            this.newlineMarked = oldNewlineMarked;
        }
    }

    // The predicate of a while loop is compiled at the end of the loop,
    // after the body, so any statements it contains (from parentheses)
    // emit their line events again even if the lines were already seen.
    @Override
    public Void visitWhileNode(Nodes.WhileNode node) {
        boolean[] oldNewlineMarked = this.newlineMarked;
        this.newlineMarked = new boolean[oldNewlineMarked.length];
        try {
            node.predicate.accept(this);
        } finally {
            this.newlineMarked = oldNewlineMarked;
        }

        if (node.statements != null) {
            node.statements.accept(this);
        }
        return null;
    }

    // The predicate of an until loop is compiled at the end of the loop,
    // after the body, so any statements it contains (from parentheses)
    // emit their line events again even if the lines were already seen.
    @Override
    public Void visitUntilNode(Nodes.UntilNode node) {
        boolean[] oldNewlineMarked = this.newlineMarked;
        this.newlineMarked = new boolean[oldNewlineMarked.length];
        try {
            node.predicate.accept(this);
        } finally {
            this.newlineMarked = oldNewlineMarked;
        }

        if (node.statements != null) {
            node.statements.accept(this);
        }
        return null;
    }

    @Override
    public Void visitIfNode(Nodes.IfNode node) {
        setNewLineFlag(node);
        return super.visitIfNode(node);
    }

    @Override
    public Void visitUnlessNode(Nodes.UnlessNode node) {
        setNewLineFlag(node);
        return super.visitUnlessNode(node);
    }

    @Override
    public Void visitStatementsNode(Nodes.StatementsNode node) {
        for (Nodes.Node child : node.body) {
            setNewLineFlag(child);
        }
        return super.visitStatementsNode(node);
    }

    // Keep in sync with the newline_flag! overrides in Ruby's newlines.rb which
    // are not part of the generated setNewLineFlag() methods.
    //
    // The line event for a statement is emitted where its first instruction is
    // compiled, so nodes whose first instruction comes from a sub-expression
    // delegate their newline flag to that sub-expression: assignments to their
    // value, calls to their receiver, and array, hash, and interpolated string
    // literals to their first element. Static literals are the exception: they
    // are compiled to a single instruction on the first line of the literal, so
    // they do not delegate.
    private void setNewLineFlag(Nodes.Node node) {
        if (node instanceof Nodes.LocalVariableWriteNode write) {
            setNewLineFlag(write.value);
        } else if (node instanceof Nodes.InstanceVariableWriteNode write) {
            setNewLineFlag(write.value);
        } else if (node instanceof Nodes.ClassVariableWriteNode write) {
            setNewLineFlag(write.value);
        } else if (node instanceof Nodes.GlobalVariableWriteNode write) {
            setNewLineFlag(write.value);
        } else if (node instanceof Nodes.ConstantWriteNode write) {
            setNewLineFlag(write.value);
        } else if (node instanceof Nodes.ConstantPathWriteNode write) {
            setNewLineFlag(write.value);
        } else if (node instanceof Nodes.MultiWriteNode write) {
            setNewLineFlag(write.value);
        } else if (node instanceof Nodes.CallNode call) {
            if (call.receiver != null) {
                setNewLineFlag(call.receiver);
            } else {
                markNewLineFlag(node);
            }
        } else if (node instanceof Nodes.ArrayNode array) {
            if (array.elements.length > 0 && !isStaticLiteral(array)) {
                setNewLineFlag(array.elements[0]);
            } else {
                markNewLineFlag(node);
            }
        } else if (node instanceof Nodes.HashNode hash) {
            if (hash.elements.length > 0 && !isStaticLiteral(hash)) {
                setNewLineFlag(hash.elements[0]);
            } else {
                markNewLineFlag(node);
            }
        } else if (node instanceof Nodes.InterpolatedStringNode string) {
            if (string.parts.length > 0 && !isStaticLiteral(string)) {
                setNewLineFlag(string.parts[0]);
            } else {
                markNewLineFlag(node);
            }
        } else if (node instanceof Nodes.IfNode ifNode) {
            setNewLineFlag(ifNode.predicate);
        } else if (node instanceof Nodes.UnlessNode unlessNode) {
            setNewLineFlag(unlessNode.predicate);
        } else if (node instanceof Nodes.RescueModifierNode rescueModifier) {
            setNewLineFlag(rescueModifier.expression);
        } else if (node instanceof Nodes.WhileNode whileNode) {
            boolean prefix = isPrefixLoop(whileNode, whileNode.isBeginModifier(), whileNode.statements);
            if (prefix && whileNode.predicate instanceof Nodes.ParenthesesNode) {
                // A parenthesized predicate emits its own line event when it is
                // compiled at the end of the loop, in addition to this one.
                markNewLineFlag(node);
            } else {
                setNewLineFlag(whileNode.predicate);
            }
        } else if (node instanceof Nodes.UntilNode untilNode) {
            boolean prefix = isPrefixLoop(untilNode, untilNode.isBeginModifier(), untilNode.statements);
            if (prefix && untilNode.predicate instanceof Nodes.ParenthesesNode) {
                // A parenthesized predicate emits its own line event when it is
                // compiled at the end of the loop, in addition to this one.
                markNewLineFlag(node);
            } else {
                setNewLineFlag(untilNode.predicate);
            }
        } else {
            node.setNewLineFlag(this.source, this.newlineMarked);
        }
    }

    // Mark the node itself, like Nodes.Node#setNewLineFlag(), regardless of any
    // setNewLineFlag() override of the node.
    private void markNewLineFlag(Nodes.Node node) {
        int line = this.source.findLine(node.startOffset);
        if (!this.newlineMarked[line]) {
            this.newlineMarked[line] = true;
            node.setNewLineFlag(true);
        }
    }

    // PM_NODE_FLAG_STATIC_LITERAL, which is serialized together with the
    // node-specific flags.
    private static final short STATIC_LITERAL_FLAG = 0x2;

    // Whether the node has the PM_NODE_FLAG_STATIC_LITERAL flag. Only nodes
    // with node-specific flags store their flags in Java, so it is computed
    // structurally for the others, for the nodes which can appear inside an
    // array or hash literal.
    private static boolean isStaticLiteral(Nodes.Node node) {
        if (node instanceof Nodes.ArrayNode array) {
            return (array.flags & STATIC_LITERAL_FLAG) != 0;
        } else if (node instanceof Nodes.InterpolatedStringNode string) {
            return (string.flags & STATIC_LITERAL_FLAG) != 0;
        } else if (node instanceof Nodes.StringNode string) {
            return (string.flags & STATIC_LITERAL_FLAG) != 0;
        } else if (node instanceof Nodes.SymbolNode symbol) {
            return (symbol.flags & STATIC_LITERAL_FLAG) != 0;
        } else if (node instanceof Nodes.IntegerNode integer) {
            return (integer.flags & STATIC_LITERAL_FLAG) != 0;
        } else if (node instanceof Nodes.RationalNode rational) {
            return (rational.flags & STATIC_LITERAL_FLAG) != 0;
        } else if (node instanceof Nodes.RegularExpressionNode regexp) {
            return (regexp.flags & STATIC_LITERAL_FLAG) != 0;
        } else if (node instanceof Nodes.RangeNode range) {
            return (range.flags & STATIC_LITERAL_FLAG) != 0;
        } else if (node instanceof Nodes.HashNode hash) {
            for (Nodes.Node element : hash.elements) {
                if (!(element instanceof Nodes.AssocNode assoc)) {
                    return false;
                }
                // An assoc node with a container key or value is never a static literal.
                if (isContainer(assoc.key) || isContainer(assoc.value)) {
                    return false;
                }
                if (!isStaticLiteral(assoc.key) || !isStaticLiteral(assoc.value)) {
                    return false;
                }
            }
            return true;
        } else {
            return node instanceof Nodes.NilNode || node instanceof Nodes.TrueNode || node instanceof Nodes.FalseNode ||
                    node instanceof Nodes.FloatNode || node instanceof Nodes.ImaginaryNode ||
                    node instanceof Nodes.SourceLineNode;
        }
    }

    private static boolean isContainer(Nodes.Node node) {
        return node instanceof Nodes.ArrayNode || node instanceof Nodes.HashNode || node instanceof Nodes.RangeNode;
    }

    // Whether a while/until loop starts with its keyword. There is no keyword
    // location in the Java nodes, but only a prefix loop starts before its
    // statements.
    private static boolean isPrefixLoop(Nodes.Node node, boolean beginModifier, Nodes.StatementsNode statements) {
        return !beginModifier && (statements == null || node.startOffset != statements.startOffset);
    }

    @Override
    protected Void defaultVisit(Nodes.Node node) {
        node.visitChildNodes(this);
        return null;
    }

}
