package org.checkerframework.checker.test.junit;

import org.checkerframework.framework.test.CheckerFrameworkPerDirectoryTest;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.List;

/**
 * JUnit tests for the Nullness checker when -AignoreCheckDeadCode command-line argument is used.
 */
public class NullnessDeadBranchTest extends CheckerFrameworkPerDirectoryTest {
    /**
     * Create a NullnessNullMarkedTest.
     *
     * @param testFiles the files containing test code, which will be type-checked
     */
    public NullnessDeadBranchTest(List<File> testFiles) {
        super(
                testFiles,
                org.checkerframework.checker.nullness.NullnessChecker.class,
                "nullness-deadbranch",
                "-AignoreCheckDeadCode");
    }

    @Parameterized.Parameters
    public static String[] getTestDirs() {
        return new String[] {"nullness-deadbranch"};
    }
}
