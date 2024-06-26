package org.checkerframework.checker.test.junit;

import org.checkerframework.framework.test.CheckerFrameworkPerDirectoryTest;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.util.List;

/** JUnit tests for the Interning checker when AwarnRedundantAnnotations is used. */
public class InterningWarnRedundantAnnotationsTest extends CheckerFrameworkPerDirectoryTest {

    /**
     * Create a InterningWarnRedundantAnnotationsTest.
     *
     * @param testFiles the files containing test code, which will be type-checked
     */
    public InterningWarnRedundantAnnotationsTest(List<File> testFiles) {
        super(
                testFiles,
                org.checkerframework.checker.interning.InterningChecker.class,
                "interning-warnredundantannotations",
                "-AwarnRedundantAnnotations");
    }

    @Parameters
    public static String[] getTestDirs() {
        return new String[] {"interning-warnredundantannotations"};
    }
}
