import org.checkerframework.checker.nullness.qual.KeyFor;

import java.util.HashMap;
import java.util.Map;

public abstract class Issue2564 {
    public enum EnumType {
        // :: error: (enum.declaration.type.incompatible)
        @KeyFor("myMap") MY_KEY,
        // :: error: (enum.declaration.type.incompatible)
        @KeyFor("enumMap") ENUM_KEY;
        private static final Map<String, Integer> enumMap = new HashMap<>();

        void method() {
            @KeyFor("enumMap") EnumType t = ENUM_KEY;
            int x = enumMap.get(ENUM_KEY);
        }
    }

    private static final Map<String, Integer> myMap = new HashMap<>();
}
