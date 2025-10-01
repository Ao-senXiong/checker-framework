package org.checkerframework.checker.test.junit;

import org.checkerframework.framework.test.CheckerFrameworkPerDirectoryTest;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.util.List;

/** JUnit tests for the arena checker. */
public class ArenaTest extends CheckerFrameworkPerDirectoryTest {

    /**
     * Create tests for arena checker.
     *
     * @param testFiles the files containing test code, which will be type-checked
     */
    public ArenaTest(List<File> testFiles) {
        super(testFiles, org.checkerframework.checker.arena.ArenaTypeChecker.class, "arena");
    }

    @Parameters
    public static String[] getTestDirs() {
        return new String[] {"arena"};
    }
}
