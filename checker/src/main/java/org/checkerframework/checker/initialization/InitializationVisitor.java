package org.checkerframework.checker.initialization;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;

import org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.NullnessChecker;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.flow.CFAbstractAnalysis.FieldInitialValue;
import org.checkerframework.framework.flow.CFAbstractStore;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;
import org.checkerframework.javacutil.AnnotationMirrorSet;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;
import org.plumelib.util.ArraysPlume;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/* NO-AFU
   import org.checkerframework.common.wholeprograminference.WholeProgramInference;
*/

/**
 * The visitor for the freedom-before-commitment type-system. The freedom-before-commitment
 * type-system and this class are abstract and need to be combined with another type-system whose
 * safe initialization should be tracked. For an example, see the {@link NullnessChecker}.
 */
public class InitializationVisitor extends BaseTypeVisitor<InitializationAnnotatedTypeFactory> {

    // Error message keys
    private static final @CompilerMessageKey String COMMITMENT_INVALID_FIELD_TYPE =
            "initialization.invalid.field.type";
    private static final @CompilerMessageKey String COMMITMENT_INVALID_CONSTRUCTOR_RETURN_TYPE =
            "initialization.invalid.constructor.return.type";
    private static final @CompilerMessageKey String
            COMMITMENT_INVALID_FIELD_WRITE_UNKNOWN_INITIALIZATION =
                    "initialization.invalid.field.write.unknown";
    private static final @CompilerMessageKey String COMMITMENT_INVALID_FIELD_WRITE_INITIALIZED =
            "initialization.invalid.field.write.initialized";

    /** List of fields in the current compilation unit that have been initialized. */
    protected final List<VariableTree> initializedFields;

    /** The value of the assumeInitialized option. */
    protected final boolean assumeInitialized;

    /**
     * Create an InitializationVisitor.
     *
     * @param checker the initialization checker
     */
    public InitializationVisitor(BaseTypeChecker checker) {
        super(checker);
        initializedFields = new ArrayList<>();
        assumeInitialized = checker.hasOption("assumeInitialized");
    }

    @Override
    protected InitializationAnnotatedTypeFactory createTypeFactory() {
        return new InitializationAnnotatedTypeFactory(checker);
    }

    @Override
    public void visit(TreePath path) {
        // This visitor does nothing if init checking is turned off.
        if (!assumeInitialized) {
            super.visit(path);
        }
    }

    @Override
    public void setRoot(CompilationUnitTree root) {
        // Clean up the cache of initialized fields once per compilation unit.
        // Alternatively, but harder to determine, this could be done once per top-level class.
        initializedFields.clear();
        super.setRoot(root);
    }

    @Override
    protected void checkConstructorInvocation(
            AnnotatedDeclaredType dt, AnnotatedExecutableType constructor, NewClassTree src) {
        // Receiver annotations for constructors are forbidden, therefore no check is necessary.
        // TODO: nested constructors can have receivers!
    }

    @Override
    protected void checkConstructorResult(
            AnnotatedExecutableType constructorType, ExecutableElement constructorElement) {
        // Nothing to check
    }

    @Override
    protected void checkThisOrSuperConstructorCall(
            MethodInvocationTree superCall, @CompilerMessageKey String errorKey) {
        // Nothing to check
    }

    @Override
    protected boolean commonAssignmentCheck(
            Tree varTree,
            ExpressionTree valueExp,
            @CompilerMessageKey String errorKey,
            Object... extraArgs) {
        // field write of the form x.f = y
        if (TreeUtils.isFieldAccess(varTree)) {
            // cast is safe: a field access can only be an IdentifierTree or MemberSelectTree
            ExpressionTree lhs = (ExpressionTree) varTree;
            ExpressionTree y = valueExp;
            VariableElement el = TreeUtils.variableElementFromUse(lhs);
            AnnotatedTypeMirror xType = atypeFactory.getReceiverType(lhs);
            AnnotatedTypeMirror yType = atypeFactory.getAnnotatedType(y);
            // the special FBC rules do not apply if there is an explicit
            // UnknownInitialization annotation
            AnnotationMirrorSet fieldAnnotations =
                    atypeFactory.getAnnotatedType(el).getAnnotations();
            if (!AnnotationUtils.containsSameByName(
                    fieldAnnotations, atypeFactory.UNKNOWN_INITIALIZATION)) {
                if (!ElementUtils.isStatic(el)
                        && !(atypeFactory.isInitialized(yType)
                                || atypeFactory.isUnderInitialization(xType)
                                || atypeFactory.isFbcBottom(yType))) {
                    @CompilerMessageKey String err;
                    if (atypeFactory.isInitialized(xType)) {
                        err = COMMITMENT_INVALID_FIELD_WRITE_INITIALIZED;
                    } else {
                        err = COMMITMENT_INVALID_FIELD_WRITE_UNKNOWN_INITIALIZATION;
                    }
                    checker.reportError(varTree, err, varTree);
                    return false; // prevent issuing another error about subtyping
                }
            }
        }
        return super.commonAssignmentCheck(varTree, valueExp, errorKey, extraArgs);
    }

    @Override
    protected void checkExceptionParameter(CatchTree node) {
        // TODO Issue 363
        // https://github.com/eisop/checker-framework/issues/363
    }

    @Override
    public void processClassTree(ClassTree tree) {
        // go through all members and look for initializers.
        // save all fields that are initialized and do not report errors about
        // them later when checking constructors.
        for (Tree member : tree.getMembers()) {
            if (member.getKind() == Tree.Kind.BLOCK && !((BlockTree) member).isStatic()) {
                BlockTree block = (BlockTree) member;
                InitializationStore store = atypeFactory.getRegularExitStore(block);

                // Add field values for fields with an initializer.
                for (FieldInitialValue<CFValue> fieldInitialValue :
                        store.getAnalysis().getFieldInitialValues()) {
                    if (fieldInitialValue.initializer != null) {
                        store.addInitializedField(fieldInitialValue.fieldDecl.getField());
                    }
                }
                List<VariableTree> init =
                        atypeFactory.getInitializedFields(store, getCurrentPath());
                initializedFields.addAll(init);
            }
        }

        super.processClassTree(tree);

        // Warn about uninitialized static fields.
        Tree.Kind nodeKind = tree.getKind();
        // Skip interfaces (and annotations, which are interfaces).  In an interface, every static
        // field must be initialized.  Java forbids uninitialized variables and static initializer
        // blocks.
        if (nodeKind != Tree.Kind.INTERFACE && nodeKind != Tree.Kind.ANNOTATION_TYPE) {
            // See GenericAnnotatedTypeFactory.performFlowAnalysis for why we use
            // the regular exit store of the class here.
            InitializationStore store = atypeFactory.getRegularExitStore(tree);
            if (store != null) {
                // Add field values for fields with an initializer.
                for (FieldInitialValue<CFValue> fieldInitialValue :
                        store.getAnalysis().getFieldInitialValues()) {
                    if (fieldInitialValue.initializer != null) {
                        store.addInitializedField(fieldInitialValue.fieldDecl.getField());
                    }
                }
            }

            List<AnnotationMirror> receiverAnnotations = Collections.emptyList();
            checkFieldsInitialized(tree, true, store, receiverAnnotations);
        }
    }

    @Override
    public Void visitMethod(MethodTree tree, Void p) {
        if (TreeUtils.isConstructor(tree)) {
            Collection<? extends AnnotationMirror> returnTypeAnnotations =
                    AnnotationUtils.getExplicitAnnotationsOnConstructorResult(tree);
            // check for invalid constructor return type
            for (Class<? extends Annotation> c : atypeFactory.getSupportedTypeQualifiers()) {
                for (AnnotationMirror a : returnTypeAnnotations) {
                    if (atypeFactory.areSameByClass(a, c)) {
                        checker.reportError(tree, COMMITMENT_INVALID_CONSTRUCTOR_RETURN_TYPE, tree);
                        break;
                    }
                }
            }

            // Check that all fields have been initialized at the end of the constructor.
            boolean isStatic = false;

            InitializationStore store = atypeFactory.getRegularExitStore(tree);
            List<? extends AnnotationMirror> receiverAnnotations = getAllReceiverAnnotations(tree);
            checkFieldsInitialized(tree, isStatic, store, receiverAnnotations);
        }
        return super.visitMethod(tree, p);
    }

    /**
     * The assignment/variable/method invocation tree currently being checked.
     *
     * <p>In the case that the right-hand side is an object, this is used by {@link
     * #reportCommonAssignmentError(AnnotatedTypeMirror, AnnotatedTypeMirror, Tree, String,
     * Object...)} to get the correct store value for the right-hand side's fields and checker
     * whether they are initialized according to the target checker.
     */
    protected Tree commonAssignmentTree;

    @Override
    public Void visitVariable(VariableTree tree, Void p) {
        Tree oldCommonAssignmentTree = commonAssignmentTree;
        commonAssignmentTree = tree;
        // is this a field (and not a local variable)?
        if (TreeUtils.elementFromDeclaration(tree).getKind().isField()) {
            Set<AnnotationMirror> annotationMirrors =
                    atypeFactory.getAnnotatedType(tree).getExplicitAnnotations();
            // Fields cannot have commitment annotations.
            for (Class<? extends Annotation> c : atypeFactory.getSupportedTypeQualifiers()) {
                for (AnnotationMirror a : annotationMirrors) {
                    if (atypeFactory.isUnknownInitialization(a)) {
                        continue; // unknown initialization is allowed
                    }
                    if (atypeFactory.areSameByClass(a, c)) {
                        checker.reportError(tree, COMMITMENT_INVALID_FIELD_TYPE, tree);
                        break;
                    }
                }
            }
        }
        super.visitVariable(tree, p);
        commonAssignmentTree = oldCommonAssignmentTree;
        return null;
    }

    @Override
    public Void visitAssignment(AssignmentTree node, Void p) {
        Tree oldCommonAssignmentTree = commonAssignmentTree;
        commonAssignmentTree = node;
        super.visitAssignment(node, p);
        commonAssignmentTree = oldCommonAssignmentTree;
        return null;
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
        Tree oldCommonAssignmentTree = commonAssignmentTree;
        commonAssignmentTree = node;
        super.visitMethodInvocation(node, p);
        commonAssignmentTree = oldCommonAssignmentTree;
        return null;
    }

    @Override
    protected void reportCommonAssignmentError(
            AnnotatedTypeMirror varType,
            AnnotatedTypeMirror valueType,
            Tree valueTree,
            @CompilerMessageKey String errorKey,
            Object... extraArgs) {
        FoundRequired pair = FoundRequired.of(valueType, varType);
        String valueTypeString = pair.found;
        String varTypeString = pair.required;

        // If the stored value of valueTree is wrong, we still do not report an error
        // if all necessary fields of valueTree are initialized in the store before the assignment.

        InitializationStore initStoreBefore = atypeFactory.getStoreBefore(commonAssignmentTree);

        // We can't check if all necessary fields are initialized without a store.
        if (initStoreBefore == null) {
            super.reportCommonAssignmentError(varType, valueType, valueTree, errorKey, extraArgs);
            return;
        }

        // We only track field initialization for the current receiver.
        if (!valueTree.toString().equals("this")) {
            super.reportCommonAssignmentError(varType, valueType, valueTree, errorKey, extraArgs);
            return;
        }

        // If the required type is @Initialized and the value type is not final,
        // we always need to report an error.
        if (varType.getAnnotation(Initialized.class) != null
                && !ElementUtils.isFinal(
                        TypesUtils.getTypeElement(valueType.getUnderlyingType()))) {
            super.reportCommonAssignmentError(varType, valueType, valueTree, errorKey, extraArgs);
            return;
        }

        // Otherwise, we check if there are any uninitialized fields and only report the error
        // if this is the case.
        GenericAnnotatedTypeFactory<?, ?, ?, ?> targetFactory =
                checker.getTypeFactoryOfSubcheckerOrNull(
                        ((InitializationChecker) checker).getTargetCheckerClass());
        List<VariableTree> uninitializedFields =
                atypeFactory.getUninitializedFields(
                        initStoreBefore,
                        targetFactory.getStoreBefore(commonAssignmentTree),
                        getCurrentPath(),
                        false,
                        Collections.emptyList());
        uninitializedFields.removeAll(initializedFields);

        if (!uninitializedFields.isEmpty()) {
            StringJoiner fieldsString = new StringJoiner(", ");
            for (VariableTree f : uninitializedFields) {
                fieldsString.add(f.getName());
            }
            checker.reportError(
                    commonAssignmentTree,
                    errorKey,
                    ArraysPlume.concatenate(extraArgs, valueTypeString, varTypeString));
        }
    }

    @Override
    protected void reportMethodInvocabilityError(
            MethodInvocationTree node, AnnotatedTypeMirror found, AnnotatedTypeMirror expected) {
        // We only track field initialization for the current receiver.
        if (!TreeUtils.isSelfAccess(node)) {
            super.reportMethodInvocabilityError(node, found, expected);
            return;
        }

        GenericAnnotatedTypeFactory<?, ?, ?, ?> targetFactory =
                checker.getTypeFactoryOfSubcheckerOrNull(
                        ((InitializationChecker) checker).getTargetCheckerClass());
        List<VariableTree> uninitializedFields =
                atypeFactory.getUninitializedFields(
                        atypeFactory.getStoreBefore(node),
                        targetFactory.getStoreBefore(node),
                        getCurrentPath(),
                        false,
                        Collections.emptyList());
        uninitializedFields.removeAll(initializedFields);

        AnnotationMirror init = expected.getAnnotation(Initialized.class);
        AnnotationMirror unknownInit = expected.getAnnotation(UnknownInitialization.class);
        AnnotationMirror underInit = expected.getAnnotation(UnderInitialization.class);

        // If the actual receiver type (found) is not a subtype of expected,
        // we still do not report an error if all necessary fields are initialized in the store
        // before the method call.

        // Find the frame for which the receiver must be initialized to discharge this error:
        // * If the expected type is @UnknownInitialization(A) or @UnderInitialization(A), the frame
        // is A.
        // * If the expected type is @Initialized and the receiver type is final, the frame
        // is the receiver type.
        // * Otherwise, this error cannot be discharged and is reported by the super method.
        TypeMirror frame;
        if (unknownInit != null) {
            frame = atypeFactory.getTypeFrameFromAnnotation(unknownInit);
        } else if (underInit != null) {
            frame = atypeFactory.getTypeFrameFromAnnotation(underInit);
        } else if (init != null
                && ElementUtils.isFinal(TypesUtils.getTypeElement(expected.getUnderlyingType()))) {
            frame = expected.getUnderlyingType();
        } else {
            if (!uninitializedFields.isEmpty()) {
                reportMethodInvocabilityErrorWithUninitializedFields(
                        node, found, expected, uninitializedFields);
            } else {
                super.reportMethodInvocabilityError(node, found, expected);
            }
            return;
        }

        TypeMirror underlyingReceiverType = atypeFactory.getReceiverType(node).getUnderlyingType();
        if (!atypeFactory
                .getProcessingEnv()
                .getTypeUtils()
                .isSubtype(frame, underlyingReceiverType)) {
            super.reportMethodInvocabilityError(node, found, expected);
            return;
        }

        if (!uninitializedFields.isEmpty()) {
            reportMethodInvocabilityErrorWithUninitializedFields(
                    node, found, expected, uninitializedFields);
        }
    }

    /**
     * Report a method invocability error with uninitialized fields.
     *
     * @param node the AST node at which to report the error
     * @param found the actual type of the receiver
     * @param expected the expected type of the receiver
     * @param uninitializedFields the list of uninitialized fields
     */
    private void reportMethodInvocabilityErrorWithUninitializedFields(
            MethodInvocationTree node,
            AnnotatedTypeMirror found,
            AnnotatedTypeMirror expected,
            List<VariableTree> uninitializedFields) {
        StringJoiner fieldsString = new StringJoiner(", ");
        for (VariableTree f : uninitializedFields) {
            fieldsString.add(f.getName());
        }
        checker.reportError(
                node,
                "initialization.method.invocation.invalid",
                TreeUtils.elementFromUse(node),
                fieldsString.toString(),
                found.toString(),
                expected.toString());
    }

    /**
     * Returns the full list of annotations on the receiver.
     *
     * @param tree a method declaration
     * @return all the annotations on the method's receiver
     */
    private List<? extends AnnotationMirror> getAllReceiverAnnotations(MethodTree tree) {
        // TODO: get access to a Types instance and use it to get receiver type
        // Or, extend ExecutableElement with such a method.
        // Note that we cannot use the receiver type from AnnotatedExecutableType, because that
        // would only have the nullness annotations; here we want to see all annotations on the
        // receiver.
        if (TreeUtils.isConstructor(tree)) {
            com.sun.tools.javac.code.Symbol meth =
                    (com.sun.tools.javac.code.Symbol) TreeUtils.elementFromDeclaration(tree);
            return meth.getRawTypeAttributes();
        }
        return Collections.emptyList();
    }

    /**
     * Checks that all fields (all static fields if {@code staticFields} is true) are initialized at
     * the end of a given constructor or static class initializer.
     *
     * @param tree a {@link ClassTree} if {@code staticFields} is true; a {@link MethodTree} for a
     *     constructor if {@code staticFields} is false. This is where errors are reported, if they
     *     are not reported at the fields themselves
     * @param staticFields whether to check static fields or instance fields
     * @param initExitStore the initialization exit store for the constructor or static initializer
     * @param receiverAnnotations the annotations on the receiver
     */
    protected void checkFieldsInitialized(
            Tree tree,
            boolean staticFields,
            InitializationStore initExitStore,
            List<? extends AnnotationMirror> receiverAnnotations) {
        // If the store is null, then the constructor cannot terminate successfully
        if (initExitStore == null) {
            return;
        }

        // Compact canonical record constructors do not generate visible assignments in the source,
        // but by definition they assign to all the record's fields so we don't need to
        // check for uninitialized fields in them:
        if (tree.getKind() == Tree.Kind.METHOD
                && TreeUtils.isCompactCanonicalRecordConstructor((MethodTree) tree)) {
            return;
        }

        GenericAnnotatedTypeFactory<?, ?, ?, ?> targetFactory =
                checker.getTypeFactoryOfSubcheckerOrNull(
                        ((InitializationChecker) checker).getTargetCheckerClass());
        // The target checker's store corresponding to initExitStore
        CFAbstractStore<?, ?> targetExitStore = targetFactory.getRegularExitStore(tree);
        List<VariableTree> uninitializedFields =
                atypeFactory.getUninitializedFields(
                        initExitStore,
                        targetExitStore,
                        getCurrentPath(),
                        staticFields,
                        receiverAnnotations);
        uninitializedFields.removeAll(initializedFields);

        // If we are checking initialization of a class's static fields or of a default constructor,
        // we issue an error for every uninitialized field at the respective field declaration.
        // If we are checking a non-default constructor, we issue a single error at the constructor
        // declaration.
        boolean errorAtField = staticFields || TreeUtils.isSynthetic((MethodTree) tree);

        String errorMsg =
                (staticFields
                        ? "initialization.static.field.uninitialized"
                        : errorAtField
                                ? "initialization.field.uninitialized"
                                : "initialization.fields.uninitialized");

        // Remove fields with a relevant @SuppressWarnings annotation
        uninitializedFields.removeIf(
                f -> checker.shouldSuppressWarnings(TreeUtils.elementFromDeclaration(f), errorMsg));

        if (!uninitializedFields.isEmpty()) {
            if (errorAtField) {
                // Issue each error at the relevant field
                for (VariableTree f : uninitializedFields) {
                    checker.reportError(f, errorMsg, f.getName());
                }
            } else {
                // Issue all the errors at the relevant constructor
                StringJoiner fieldsString = new StringJoiner(", ");
                for (VariableTree f : uninitializedFields) {
                    fieldsString.add(f.getName());
                }
                checker.reportError(tree, errorMsg, fieldsString);
            }
        }

        /* NO-AFU
        // Support -Ainfer command-line argument.
        WholeProgramInference wpi = atypeFactory.getWholeProgramInference();
        if (wpi != null) {
          // For each uninitialized field, treat it as if the default value is assigned to it.
          List<VariableTree> uninitFields = new ArrayList<>(violatingFields);
          uninitFields.addAll(nonviolatingFields);
          for (VariableTree fieldTree : uninitFields) {
            Element elt = TreeUtils.elementFromDeclaration(fieldTree);
            wpi.updateFieldFromType(
                fieldTree,
                elt,
                fieldTree.getName().toString(),
                atypeFactory.getDefaultValueAnnotatedType(elt.asType()));
          }
        }
        */
    }
}
