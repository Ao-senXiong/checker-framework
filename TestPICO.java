import org.checkerframework.checker.pico.qual.Mutable;
import org.checkerframework.checker.pico.qual.Readonly;
import org.checkerframework.checker.pico.qual.ReceiverDependentMutable;
public class TestPICO {

    @ReceiverDependentMutable Object @ReceiverDependentMutable [] objArray;
    Object test(Object o) {
        Object obj = null;
        return obj;
    }

    Object[] test(@Readonly TestPICO this) {
        return this.objArray;
    }
}
