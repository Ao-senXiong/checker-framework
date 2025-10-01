package org.checkerframework.checker.arena.qual;

import org.checkerframework.framework.qual.JavaExpression;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ClassArena {
    @JavaExpression
    String[] value() default {};
}
