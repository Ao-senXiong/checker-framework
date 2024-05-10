package org.checkerframework.checker.immutability;

import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;

import org.checkerframework.checker.immutability.qual.Bottom;
import org.checkerframework.checker.immutability.qual.Immutable;
import org.checkerframework.checker.immutability.qual.ReceiverDependantMutable;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeValidator;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreePathUtil;
import org.checkerframework.javacutil.TreeUtils;

import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.VariableElement;

/**
 * Enforce correct usage of immutability and assignability qualifiers. TODO @PolyMutable is only
 * used on constructor/method parameters or method return
 */
public class PICOValidator extends BaseTypeValidator {

    public final PICONoInitAnnotatedTypeFactory picoTypeFactory;

    public PICOValidator(
            BaseTypeChecker checker,
            BaseTypeVisitor<?> visitor,
            PICONoInitAnnotatedTypeFactory atypeFactory) {
        super(checker, visitor, atypeFactory);
        this.picoTypeFactory = atypeFactory;
    }

    @Override
    public Void visitDeclared(AnnotatedDeclaredType type, Tree tree) {
        checkStaticReceiverDependantMutableError(type, tree);
        checkImplicitlyImmutableTypeError(type, tree);
        checkOnlyOneAssignabilityModifierOnField(tree);

        return super.visitDeclared(type, tree);
    }

    @Override
    protected boolean shouldCheckTopLevelDeclaredOrPrimitiveType(
            AnnotatedTypeMirror type, Tree tree) {
        // check top annotations in extends/implements clauses
        if ((tree.getKind() == Kind.IDENTIFIER || tree.getKind() == Kind.PARAMETERIZED_TYPE)
                && PICONoInitAnnotatedTypeFactory.PICOSuperClauseAnnotator.isSuperClause(
                        atypeFactory.getPath(tree))) {
            return true;
        }
        // allow RDM on mutable fields with enclosing class bounded with mutable
        if (tree instanceof VariableTree) {
            VariableElement element = TreeUtils.elementFromDeclaration((VariableTree) tree);
            if (element.getKind() == ElementKind.FIELD
                    && ElementUtils.enclosingTypeElement(element) != null) {
                Set<AnnotationMirror> enclosingBound =
                        atypeFactory.getTypeDeclarationBounds(
                                Objects.requireNonNull(ElementUtils.enclosingTypeElement(element))
                                        .asType());

                Set<AnnotationMirror> declaredBound =
                        atypeFactory.getTypeDeclarationBounds(type.getUnderlyingType());

                if (AnnotationUtils.containsSameByName(declaredBound, picoTypeFactory.MUTABLE)
                        && type.hasAnnotation(ReceiverDependantMutable.class)
                        && AnnotationUtils.containsSameByName(
                                enclosingBound, picoTypeFactory.MUTABLE)) {
                    return false;
                }
            }
        }

        return super.shouldCheckTopLevelDeclaredOrPrimitiveType(type, tree);
    }

    @Override
    public Void visitArray(AnnotatedArrayType type, Tree tree) {
        checkStaticReceiverDependantMutableError(type, tree);
        // Array can not be implicitly immutable
        return super.visitArray(type, tree);
    }

    @Override
    public Void visitPrimitive(AnnotatedPrimitiveType type, Tree tree) {
        checkImplicitlyImmutableTypeError(type, tree);
        return super.visitPrimitive(type, tree);
    }

    private void checkStaticReceiverDependantMutableError(AnnotatedTypeMirror type, Tree tree) {
        if (!type.isDeclaration() // variables in static contexts and static fields use class
                // decl as enclosing type
                && PICOTypeUtil.inStaticScope(visitor.getCurrentPath())
                && !""
                        .contentEquals(
                                Objects.requireNonNull(
                                                TreePathUtil.enclosingClass(
                                                        visitor.getCurrentPath()))
                                        .getSimpleName()) // Exclude @RDM usages in anonymous
                // classes
                && type.hasAnnotation(ReceiverDependantMutable.class)) {
            reportValidityResult("static.receiverdependantmutable.forbidden", type, tree);
        }
    }

    /**
     * Check that implicitly immutable type has immutable or bottom type. Dataflow might refine
     * immutable type to {@code @Bottom} (see RefineFromNull.java), so we accept @Bottom as a valid
     * qualifier for implicitly immutable types
     */
    private void checkImplicitlyImmutableTypeError(AnnotatedTypeMirror type, Tree tree) {
        if (PICOTypeUtil.isImplicitlyImmutableType(type)
                && !type.hasAnnotation(Immutable.class)
                && !type.hasAnnotation(Bottom.class)) {
            reportInvalidAnnotationsOnUse(type, tree);
        }
    }

    /**
     * Ensures the well-formdness in terms of assignability on a field. This covers both instance
     * fields and static fields.
     */
    private void checkOnlyOneAssignabilityModifierOnField(Tree tree) {
        if (tree.getKind() == Kind.VARIABLE) {
            VariableTree variableTree = (VariableTree) tree;
            VariableElement variableElement = TreeUtils.elementFromDeclaration(variableTree);
            if (!PICOTypeUtil.hasOneAndOnlyOneAssignabilityQualifier(
                    variableElement, atypeFactory)) {
                reportFieldMultipleAssignabilityModifiersError(variableElement);
            }
        }
    }

    private void reportFieldMultipleAssignabilityModifiersError(VariableElement field) {
        checker.reportError(field, "one.assignability.invalid", field);
        isValid = false;
    }
}
