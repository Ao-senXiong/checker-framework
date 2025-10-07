package org.checkerframework.checker.arena;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.MostlyNoElementQualifierHierarchy;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.util.QualifierKind;
import org.checkerframework.javacutil.AnnotationUtils;

import javax.lang.model.element.AnnotationMirror;

public class ArenaAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    @SuppressWarnings("this-escape")
    public ArenaAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);
        postInit();
    }

    @Override
    protected QualifierHierarchy createQualifierHierarchy() {
        return new ArenaAnnotatedTypeFactory.ArenaQualifierHierarchy();
    }

    // TODO: implment subtype bewteen @Arena("x") = @Arena("y")
    class ArenaQualifierHierarchy extends MostlyNoElementQualifierHierarchy {
        protected ArenaQualifierHierarchy() {
            super(
                    ArenaAnnotatedTypeFactory.this.getSupportedTypeQualifiers(),
                    elements,
                    ArenaAnnotatedTypeFactory.this);
        }

        @Override
        protected boolean isSubtypeWithElements(
                AnnotationMirror subAnno,
                QualifierKind subKind,
                AnnotationMirror superAnno,
                QualifierKind superKind) {
            // If the arena name is the same, then return true
            return AnnotationUtils.areSame(subAnno, superAnno);
        }

        @Override
        protected AnnotationMirror leastUpperBoundWithElements(
                AnnotationMirror a1,
                QualifierKind qualifierKind1,
                AnnotationMirror a2,
                QualifierKind qualifierKind2,
                QualifierKind lubKind) {
            return null;
        }

        @Override
        protected AnnotationMirror greatestLowerBoundWithElements(
                AnnotationMirror a1,
                QualifierKind qualifierKind1,
                AnnotationMirror a2,
                QualifierKind qualifierKind2,
                QualifierKind glbKind) {
            return null;
        }
    }
}
