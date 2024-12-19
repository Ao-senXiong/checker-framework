import org.checkerframework.checker.pico.qual.Immutable;
import org.checkerframework.checker.pico.qual.Readonly;
public class TestGenericsPICO<T> {

    Inner<@Immutable T, @Readonly Object> f;

    TestGenericsPICO(){
        f = new Inner<@Immutable T, @Readonly Object>();
    };

    class Inner<E, F>{
        Inner(){}
    }
}
