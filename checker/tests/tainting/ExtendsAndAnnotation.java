// Test case for issue 278: https://github.com/typetools/checker-framework/issues/278

import org.checkerframework.checker.tainting.qual.PolyTainted;
import org.checkerframework.checker.tainting.qual.Tainted;
import org.checkerframework.checker.tainting.qual.Untainted;
import org.checkerframework.framework.qual.HasQualifierParameter;

// :: error: (annotation.on.supertype)
public class ExtendsAndAnnotation extends @Tainted Object {
    void test(@Untainted ExtendsAndAnnotation c) {
        // :: warning: (cast.unsafe.constructor.invocation)
        Object o = new @Untainted ExtendsAndAnnotation();
        o = new @Tainted ExtendsAndAnnotation();
    }

    @HasQualifierParameter(Tainted.class)
    // :: error: (invalid.polymorphic.qualifier)
    // :: error: (declaration.inconsistent.with.extends.clause)
    // :: error: (annotation.on.supertype)
    static class Banana extends @PolyTainted Object {}
}
