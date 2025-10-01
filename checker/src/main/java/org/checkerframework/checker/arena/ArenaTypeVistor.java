package org.checkerframework.checker.arena;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;

public class ArenaTypeVistor extends BaseTypeVisitor<ArenaAnnotatedTypeFactory> {

    public ArenaTypeVistor(BaseTypeChecker checker) {
        super(checker);
    }

    @Override
    public ArenaAnnotatedTypeFactory createTypeFactory() {
        return new ArenaAnnotatedTypeFactory(checker);
    }
}
