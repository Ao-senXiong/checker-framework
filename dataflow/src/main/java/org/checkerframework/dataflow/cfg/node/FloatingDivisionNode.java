package org.checkerframework.dataflow.cfg.node;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.Tree;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Objects;

/**
 * A node for the floating-point division:
 *
 * <pre>
 *   <em>expression</em> / <em>expression</em>
 * </pre>
 */
public class FloatingDivisionNode extends BinaryOperationNode {

    /**
     * Constructs a {@link FloatingDivisionNode}.
     *
     * @param tree the binary tree
     * @param left the left operand
     * @param right the right operand
     */
    public FloatingDivisionNode(BinaryTree tree, Node left, Node right) {
        super(tree, left, right);
        assert tree.getKind() == Tree.Kind.DIVIDE;
    }

    @Override
    public <R, P> R accept(NodeVisitor<R, P> visitor, P p) {
        return visitor.visitFloatingDivision(this, p);
    }

    @Override
    public String toString() {
        return "(" + getLeftOperand() + " / " + getRightOperand() + ")";
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof FloatingDivisionNode)) {
            return false;
        }
        FloatingDivisionNode other = (FloatingDivisionNode) obj;
        return getLeftOperand().equals(other.getLeftOperand())
                && getRightOperand().equals(other.getRightOperand());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLeftOperand(), getRightOperand());
    }
}
