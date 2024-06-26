package nullness.generics;

// see also framework/tests/all-systems/WildcardSuper2

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.List;

interface ToOverride<T> {
    public abstract int transform(List<? super T> function);
}

public class WildcardOverride implements ToOverride<Object> {
    @Override
    public int transform(List<Object> function) {
        return 0;
    }
}

interface ToOverride2<T> {
    // :: error: (bound.type.incompatible)
    public abstract int transform(List<@NonNull ? super T> function);
}

class WildcardOverride2 implements ToOverride2<Object> {
    @Override
    public int transform(List<Object> function) {
        return 0;
    }
}
