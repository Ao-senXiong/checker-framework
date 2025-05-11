import org.checkerframework.checker.pico.qual.ReceiverDependentMutable;

@ReceiverDependentMutable public class TestPICOInner {
    Object o;

    @ReceiverDependentMutable class Inner{
        Object o = TestPICOInner.this.o;
    }
}
