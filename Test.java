import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
// lazy final -(call method, direct assignment)> final
public class Test {
    @LazyFinal Object o;

    @Assignfields({o})
    public void test1() {
        o = new Object();
    }
    @Assignfields({o})
    public void test2() {
        o = new Object();
    }

    Test test = new test();
    test.test1();
    @Field(o:lazyfinal) test -> @Field(o.final) test
}
