package org.checkerframework.framework.type;

import org.checkerframework.framework.qual.AnnotatedFor;
import org.checkerframework.framework.util.QualifierKind;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.BugInCF;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

/**
 * A {@link org.checkerframework.framework.type.QualifierHierarchy} where, when a qualifier has
 * arguments, the subtype relation is determined by a subset test on the elements (arguments). The
 * elements must be strings.
 *
 * <p>This assumes that if the lub or glb of two qualifiers has elements, then both of the arguments
 * had the same kind as the result does.
 */
@AnnotatedFor("nullness")
public class SubtypeIsSubsetQualifierHierarchy extends MostlyNoElementQualifierHierarchy {

    /** The processing environment; used for creating annotations. */
    private final ProcessingEnvironment processingEnv;

    /**
     * Creates a SubtypeIsSubsetQualifierHierarchy from the given classes.
     *
     * @param qualifierClasses classes of annotations that are the qualifiers for this hierarchy
     * @param processingEnv processing environment
     * @param atypeFactory the associated type factory
     */
    @SuppressWarnings({
        "keyfor:argument.type.incompatible",
        "nullness:argument.type.incompatible"
    }) // TODO: EISOP Issue #1193
    public SubtypeIsSubsetQualifierHierarchy(
            Collection<Class<? extends Annotation>> qualifierClasses,
            ProcessingEnvironment processingEnv,
            GenericAnnotatedTypeFactory<?, ?, ?, ?> atypeFactory) {
        super(qualifierClasses, processingEnv.getElementUtils(), atypeFactory);
        this.processingEnv = processingEnv;
    }

    @Override
    protected boolean isSubtypeWithElements(
            AnnotationMirror subAnno,
            QualifierKind subKind,
            AnnotationMirror superAnno,
            QualifierKind superKind) {
        if (subKind == superKind) {
            List<String> superValues = valuesStringList(superAnno);
            List<String> subValues = valuesStringList(subAnno);
            return superValues.containsAll(subValues);
        }
        return subKind.isSubtypeOf(superKind);
    }

    @Override
    protected AnnotationMirror leastUpperBoundWithElements(
            AnnotationMirror a1,
            QualifierKind qualifierKind1,
            AnnotationMirror a2,
            QualifierKind qualifierKind2,
            QualifierKind lubKind) {
        if (qualifierKind1 == qualifierKind2) {
            List<String> a1Values = valuesStringList(a1);
            List<String> a2Values = valuesStringList(a2);
            Set<String> set = new LinkedHashSet<>(a1Values);
            set.addAll(a2Values);
            return createAnnotationMirrorWithValue(lubKind, set);
        } else if (lubKind == qualifierKind1) {
            return a1;
        } else if (lubKind == qualifierKind2) {
            return a2;
        } else {
            throw new BugInCF(
                    "Unexpected QualifierKinds %s %s", qualifierKind1, qualifierKind2, lubKind);
        }
    }

    @Override
    protected AnnotationMirror greatestLowerBoundWithElements(
            AnnotationMirror a1,
            QualifierKind qualifierKind1,
            AnnotationMirror a2,
            QualifierKind qualifierKind2,
            QualifierKind glbKind) {
        if (qualifierKind1 == qualifierKind2) {
            List<String> a1Values = valuesStringList(a1);
            List<String> a2Values = valuesStringList(a2);
            Set<String> set = new LinkedHashSet<>(a1Values);
            set.retainAll(a2Values);
            return createAnnotationMirrorWithValue(glbKind, set);
        } else if (glbKind == qualifierKind1) {
            return a1;
        } else if (glbKind == qualifierKind2) {
            return a2;
        } else {
            throw new BugInCF(
                    "Unexpected QualifierKinds %s %s", qualifierKind1, qualifierKind2, glbKind);
        }
    }

    /**
     * Returns a mutable list containing the {@code values} element of the given annotation. The
     * {@code values} element must be an array of strings.
     *
     * @param anno an annotation
     * @return a mutable list containing the {@code values} element; may be the empty list
     */
    private List<String> valuesStringList(AnnotationMirror anno) {
        @SuppressWarnings("deprecation") // concrete annotation class is not known
        List<String> result =
                AnnotationUtils.getElementValueArray(anno, "value", String.class, true);
        return result;
    }

    /**
     * Returns an AnnotationMirror corresponding to the given kind and values.
     *
     * @param kind the qualifier kind
     * @param values the annotation's {@code values} element/argument
     * @return an annotation of the given kind and values
     */
    private AnnotationMirror createAnnotationMirrorWithValue(
            QualifierKind kind, Set<String> values) {
        AnnotationBuilder builder = new AnnotationBuilder(processingEnv, kind.getAnnotationClass());
        builder.setValue("value", values.toArray());
        return builder.build();
    }
}
