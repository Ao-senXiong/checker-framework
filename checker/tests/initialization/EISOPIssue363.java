// Test case for issue 363:
// https://github.com/eisop/checker-framework/issues/363

import org.checkerframework.checker.initialization.qual.NotOnlyInitialized;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;

public class EISOPIssue363 {
    class MyException extends Exception {
        @NotOnlyInitialized EISOPIssue363 cause;

        MyException(EISOPIssue363 cause) {
            this.cause = cause;
        }

        MyException(@UnderInitialization EISOPIssue363 cause, String dummy) {
            this.cause = cause;
        }

        MyException(@UnknownInitialization EISOPIssue363 cause, int dummy) {
            this.cause = cause;
        }
    }

    Object field;

    EISOPIssue363() throws MyException {
        // :: error: (throw.type.invalid)
        // :: error: (argument.type.incompatible)
        throw new MyException(this);
    }

    EISOPIssue363(boolean dummy1, boolean dummy2) throws MyException {
        // :: error: (throw.type.invalid)
        throw new MyException(this, 0);
    }

    EISOPIssue363(boolean dummy1, boolean dummy2, boolean dummy3) throws MyException {
        // :: error: (throw.type.invalid)
        throw new MyException(this, "UnderInitialization");
    }

    EISOPIssue363(int dummy) throws @UnknownInitialization MyException {
        throw new MyException(this, 0);
    }

    EISOPIssue363(int dummy1, int dummy2) throws @UnknownInitialization MyException {
        throw new MyException(this, "UnderInitialization");
    }

    EISOPIssue363(int dummy1, int dummy2, int dummy3) throws @UnknownInitialization MyException {
        // :: error: (argument.type.incompatible)
        throw new MyException(this);
    }

    EISOPIssue363(String dummy) throws @UnderInitialization MyException {
        throw new MyException(this, "UnderInitialization");
    }

    EISOPIssue363(String dummy1, String dummy2) throws @UnderInitialization MyException {
        throw new MyException(this, 0);
    }

    EISOPIssue363(String dummy1, String dummy2, String dummy3)
            throws @UnderInitialization MyException {
        // :: error: (argument.type.incompatible)
        throw new MyException(this);
    }

    void test1() {
        try {
            EISOPIssue363 obj = new EISOPIssue363(1);
        } catch (MyException ex) {
            ex.cause.field.toString();
        }
    }

    void test2() {
        try {
            EISOPIssue363 obj = new EISOPIssue363();
        } catch (@UnknownInitialization MyException ex) {
            // :: error: (dereference.of.nullable)
            ex.cause.field.toString();
        }
    }

    void test3() {
        try {
            EISOPIssue363 obj = new EISOPIssue363();
        } catch (@UnderInitialization MyException ex) {
            // :: error: (dereference.of.nullable)
            ex.cause.field.toString();
        }
    }

    void test4() {
        try {
            EISOPIssue363 obj = new EISOPIssue363(1);
        } catch (MyException ex) {
            ex.cause.field.toString();
        }
    }

    void test5() {
        try {
            EISOPIssue363 obj = new EISOPIssue363(1);
        } catch (@UnknownInitialization MyException ex) {
            // :: error: (dereference.of.nullable)
            ex.cause.field.toString();
        }
    }

    void test6() {
        try {
            EISOPIssue363 obj = new EISOPIssue363(1);
        } catch (@UnderInitialization MyException ex) {
            // :: error: (dereference.of.nullable)
            ex.cause.field.toString();
        }
    }

    void test7() {
        try {
            EISOPIssue363 obj = new EISOPIssue363("UnderInitialization");
        } catch (MyException ex) {
            ex.cause.field.toString();
        }
    }

    void test8() {
        try {
            EISOPIssue363 obj = new EISOPIssue363("UnderInitialization");
        } catch (@UnknownInitialization MyException ex) {
            // :: error: (dereference.of.nullable)
            ex.cause.field.toString();
        }
    }

    void test9() {
        try {
            EISOPIssue363 obj = new EISOPIssue363("UnderInitialization");
        } catch (@UnderInitialization MyException ex) {
            // :: error: (dereference.of.nullable)
            ex.cause.field.toString();
        }
    }

    <X extends Throwable> void throwIfInstanceOf(Throwable throwable, Class<X> declaredType)
            throws X {
        if (declaredType.isInstance(throwable)) {
            throw declaredType.cast(throwable);
        }
    }

    class MyException1 extends Exception {
        @NotOnlyInitialized EISOPIssue363 cause;

        MyException1(EISOPIssue363 cause) {
            this.cause = cause;
        }

        MyException1(@UnderInitialization EISOPIssue363 cause, String dummy) {
            this.cause = cause;
        }

        MyException1(@UnknownInitialization EISOPIssue363 cause, int dummy) {
            this.cause = cause;
        }
    }

    class MyException2 extends Exception {
        @NotOnlyInitialized EISOPIssue363 cause;

        MyException2(EISOPIssue363 cause) {
            this.cause = cause;
        }

        MyException2(@UnderInitialization EISOPIssue363 cause, String dummy) {
            this.cause = cause;
        }

        MyException2(@UnknownInitialization EISOPIssue363 cause, int dummy) {
            this.cause = cause;
        }
    }

    EISOPIssue363(int dummy, Object obj) throws MyException1, MyException2 {
        // :: error: (throw.type.invalid)
        throw new MyException1(this, 0);
    }

    EISOPIssue363(int dummy1, int dummy2, Object obj) throws MyException1, MyException2 {
        // :: error: (throw.type.invalid)
        throw new MyException2(this, 0);
    }

    EISOPIssue363(boolean dummy, Object obj)
            throws @UnknownInitialization MyException1, MyException2 {
        throw new MyException1(this, 0);
    }

    EISOPIssue363(boolean dummy1, boolean dummy2, Object obj)
            throws @UnknownInitialization MyException1, MyException2 {
        // :: error: (throw.type.invalid)
        throw new MyException2(this, 0);
    }

    EISOPIssue363(boolean dummy1, boolean dummy2, boolean dummy3, Object obj)
            throws MyException1, @UnknownInitialization MyException2 {
        // :: error: (throw.type.invalid)
        throw new MyException1(this, 0);
    }

    EISOPIssue363(boolean dummy1, boolean dummy2, boolean dummy3, boolean dummy4, Object obj)
            throws MyException1, @UnknownInitialization MyException2 {
        throw new MyException2(this, 0);
    }

    EISOPIssue363(String dummy, Object obj) throws @UnderInitialization MyException1, MyException2 {
        throw new MyException1(this, "UnderInitialization");
    }

    EISOPIssue363(String dummy1, String dummy2, Object obj)
            throws @UnderInitialization MyException1, MyException2 {
        // :: error: (throw.type.invalid)
        throw new MyException2(this, "UnderInitialization");
    }

    EISOPIssue363(String dummy1, String dummy2, String dummy3, Object obj)
            throws MyException1, @UnderInitialization MyException2 {
        // :: error: (throw.type.invalid)
        throw new MyException1(this, "UnderInitialization");
    }

    EISOPIssue363(String dummy1, String dummy2, String dummy3, String dummy4, Object obj)
            throws MyException1, @UnderInitialization MyException2 {
        throw new MyException2(this, "UnderInitialization");
    }
}
