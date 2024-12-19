//import org.checkerframework.checker.pico.qual.PolyMutable;
import org.checkerframework.checker.nullness.qual.PolyNull;

public class TestPoly<T> {

    Inner<T> inner;

//    Object[] toArray(TestPoly<@PolyNull T> this) {
//        return inner.toArray();
//    }
//    class Inner<E> {
//        @PolyNull Object[] toArray(Inner<@PolyNull E> this){
//            return new @PolyNull Object[0];
//        }
//    }

    @PolyMutable Object[] toArray(TestPoly<@PolyMutable T> this) {
        return inner.toArray();
    }
    class Inner<E> {
        @PolyMutable Object[] toArray(Inner<@PolyMutable E> this){
            return new @PolyMutable Object[0];
        }
    }
}
