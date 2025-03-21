package org.checkerframework.dataflow.expression;

import com.sun.tools.javac.code.Symbol.VarSymbol;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.javacutil.AnnotationProvider;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TypesUtils;

import java.util.Objects;

import javax.lang.model.element.VariableElement;

/**
 * A local variable.
 *
 * <p>This class also represents a formal parameter expressed using its name. Class {@link
 * FormalParameter} represents a formal parameter expressed using the "#2" notation.
 */
public class LocalVariable extends JavaExpression {
    /** The element for this local variable. */
    protected final VariableElement element;

    /**
     * Creates a new LocalVariable.
     *
     * @param localVar a CFG local variable
     */
    public LocalVariable(LocalVariableNode localVar) {
        super(localVar.getType());
        this.element = localVar.getElement();
    }

    /**
     * Creates a new LocalVariable.
     *
     * @param element the element for the local variable
     */
    public LocalVariable(VariableElement element) {
        super(ElementUtils.getType(element));
        this.element = element;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof LocalVariable)) {
            return false;
        }
        LocalVariable other = (LocalVariable) obj;

        return sameElement(element, other.element);
    }

    /**
     * Returns true if the two elements are the same.
     *
     * @param element1 the first element to compare
     * @param element2 the second element to compare
     * @return true if the two elements are the same
     */
    protected static boolean sameElement(VariableElement element1, VariableElement element2) {
        VarSymbol vs1 = (VarSymbol) element1;
        VarSymbol vs2 = (VarSymbol) element2;
        // If a LocalVariable is created via JavaExpressionParseUtil#parse, then `vs1.equals(vs2)`
        // will not return true even if the elements represent the same local variable.
        // The owner of a lambda parameter is the enclosing method, so a local variable and a lambda
        // parameter might have the same name and the same owner. Use pos to differentiate this
        // case.
        return vs1.pos == vs2.pos && vs1.name == vs2.name && vs1.owner.equals(vs2.owner);
    }

    /**
     * Returns the element for this variable.
     *
     * @return the element for this variable
     */
    public VariableElement getElement() {
        return element;
    }

    @Override
    public int hashCode() {
        VarSymbol vs = (VarSymbol) element;
        return Objects.hash(vs.pos, vs.name, vs.owner);
    }

    @Override
    public String toString() {
        return element.toString();
    }

    @Override
    public String toStringDebug() {
        return super.toStringDebug() + " [owner=" + ((VarSymbol) element).owner + "]";
    }

    @SuppressWarnings("unchecked") // generic cast
    @Override
    public <T extends JavaExpression> @Nullable T containedOfClass(Class<T> clazz) {
        return getClass() == clazz ? (T) this : null;
    }

    @Override
    public boolean isDeterministic(AnnotationProvider provider) {
        return true;
    }

    @Override
    public boolean syntacticEquals(JavaExpression je) {
        if (!(je instanceof LocalVariable)) {
            return false;
        }
        LocalVariable other = (LocalVariable) je;
        return this.equals(other);
    }

    @Override
    public boolean containsSyntacticEqualJavaExpression(JavaExpression other) {
        return syntacticEquals(other);
    }

    @Override
    public boolean isAssignableByOtherCode() {
        return false;
    }

    @Override
    public boolean isModifiableByOtherCode() {
        return !TypesUtils.isImmutableTypeInJdk(((VarSymbol) element).type);
    }

    @Override
    public <R, P> R accept(JavaExpressionVisitor<R, P> visitor, P p) {
        return visitor.visitLocalVariable(this, p);
    }
}
