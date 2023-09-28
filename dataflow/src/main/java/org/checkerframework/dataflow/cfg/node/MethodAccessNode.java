package org.checkerframework.dataflow.cfg.node;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.SideEffectFree;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreeUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;

/**
 * A node for a method access, including a receiver:
 *
 * <pre>
 *   <em>expression</em> . <em>method</em> ()
 * </pre>
 */
public class MethodAccessNode extends Node {

    /** The corresponding Expression tree. */
    protected final ExpressionTree tree;

    /** The ExecutatbleElement method. */
    protected final ExecutableElement method;

    /** The receiver node for method being accessed. */
    protected final Node receiver;

    /**
     * Create a new MethodAccessNode.
     *
     * @param tree the expression that is a method access
     * @param receiver the receiver
     */
    public MethodAccessNode(ExpressionTree tree, Node receiver) {
        super(TreeUtils.typeOf(tree));
        assert TreeUtils.isMethodAccess(tree);
        this.tree = tree;
        assert TreeUtils.isUseOfElement(tree) : "@AssumeAssertion(nullness): tree kind";
        ExecutableElement method = (ExecutableElement) TreeUtils.elementFromUse(tree);
        if (method == null) {
            throw new BugInCF("tree %s [%s]", tree, tree.getClass());
        }
        this.method = method;
        this.receiver = receiver;
    }

    public ExecutableElement getMethod() {
        return method;
    }

    public Node getReceiver() {
        return receiver;
    }

    @Override
    public Tree getTree() {
        return tree;
    }

    @Override
    public <R, P> R accept(NodeVisitor<R, P> visitor, P p) {
        return visitor.visitMethodAccess(this, p);
    }

    @Override
    public String toString() {
        return getReceiver() + "." + method.getSimpleName();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof MethodAccessNode)) {
            return false;
        }
        MethodAccessNode other = (MethodAccessNode) obj;
        return getReceiver().equals(other.getReceiver()) && getMethod().equals(other.getMethod());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getReceiver(), getMethod());
    }

    @Override
    @SideEffectFree
    public Collection<Node> getOperands() {
        return Collections.singletonList(receiver);
    }

    /**
     * Retrieves the modifiers of the method.
     *
     * @return the set of modifiers of the method
     */
    public Set<Modifier> getMethodModifiers() {
        return method.getModifiers();
    }

    /**
     * Check the method is a static or not.
     *
     * @return a boolean indicates whether the method is static
     */
    public boolean isStatic() {
        return ElementUtils.isStatic(getMethod());
    }
}
