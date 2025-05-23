// This is a test for what happens when there's a missing MCA return type.

import org.checkerframework.checker.calledmethods.qual.*;
import org.checkerframework.checker.mustcall.qual.*;

import java.io.*;

class MustCallAliasPassthroughWrong3 {

    // :: warning: (mustcallalias.method.return.and.param)
    static InputStream missingMCA(@MustCallAlias InputStream is) {
        // :: error: (return.type.incompatible)
        return is;
    }

    static @MustCallAlias InputStream withMCA(@MustCallAlias InputStream is) {
        return is;
    }

    // :: error: (required.method.not.called)
    void use_bad(@Owning InputStream is) throws Exception {
        InputStream is2 = missingMCA(is);
        is2.close();
    }

    void use_good(@Owning InputStream is) throws Exception {
        InputStream is2 = withMCA(is);
        is2.close();
    }
}
