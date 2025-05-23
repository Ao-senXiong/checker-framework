package org.checkerframework.framework.test;

import org.checkerframework.framework.test.diagnostics.TestDiagnostic;
import org.checkerframework.framework.test.diagnostics.TestDiagnosticUtils;
import org.plumelib.util.StringsPlume;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Represents the test results from typechecking one or more Java files using the given
 * TestConfiguration.
 */
public class TypecheckResult {
    private final TestConfiguration configuration;
    private final CompilationResult compilationResult;
    private final List<TestDiagnostic> expectedDiagnostics;

    private final List<TestDiagnostic> missingDiagnostics;
    private final List<TestDiagnostic> unexpectedDiagnostics;

    protected TypecheckResult(
            TestConfiguration configuration,
            CompilationResult compilationResult,
            List<TestDiagnostic> expectedDiagnostics,
            List<TestDiagnostic> missingDiagnostics,
            List<TestDiagnostic> unexpectedDiagnostics) {
        this.configuration = configuration;
        this.compilationResult = compilationResult;
        this.expectedDiagnostics = expectedDiagnostics;
        this.missingDiagnostics = missingDiagnostics;
        this.unexpectedDiagnostics = unexpectedDiagnostics;
    }

    public TestConfiguration getConfiguration() {
        return configuration;
    }

    public CompilationResult getCompilationResult() {
        return compilationResult;
    }

    public List<Diagnostic<? extends JavaFileObject>> getActualDiagnostics() {
        return compilationResult.getDiagnostics();
    }

    public List<TestDiagnostic> getExpectedDiagnostics() {
        return expectedDiagnostics;
    }

    public boolean didTestFail() {
        return !unexpectedDiagnostics.isEmpty() || !missingDiagnostics.isEmpty();
    }

    public List<TestDiagnostic> getMissingDiagnostics() {
        return missingDiagnostics;
    }

    public List<TestDiagnostic> getUnexpectedDiagnostics() {
        return unexpectedDiagnostics;
    }

    public List<String> getErrorHeaders() {
        List<String> errorHeaders = new ArrayList<>();

        // none of these should be true if the test didn't fail
        if (didTestFail()) {
            if (compilationResult.compiledWithoutError() && !expectedDiagnostics.isEmpty()) {
                errorHeaders.add(
                        "The test run was expected to issue errors/warnings, but it did not.");

            } else if (!compilationResult.compiledWithoutError() && expectedDiagnostics.isEmpty()) {
                errorHeaders.add(
                        "The test run was not expected to issue errors/warnings, but it did.");
            }

            if (!unexpectedDiagnostics.isEmpty() || !missingDiagnostics.isEmpty()) {
                int numExpected = expectedDiagnostics.size();
                int numFound = numExpected - missingDiagnostics.size();
                errorHeaders.add(
                        numFound
                                + " out of "
                                + StringsPlume.nplural(numExpected, "expected diagnostic")
                                + " "
                                + (numFound == 1 ? "was" : "were")
                                + " found.");
            }
        }

        return errorHeaders;
    }

    /**
     * Summarize unexpected and missing diagnostics.
     *
     * @return summary of failures
     */
    public String summarize() {
        if (!didTestFail()) {
            return "";
        }
        StringJoiner summaryBuilder = new StringJoiner(System.lineSeparator());
        summaryBuilder.add(StringsPlume.joinLines(getErrorHeaders()));

        if (!unexpectedDiagnostics.isEmpty()) {
            int numUnexpected = unexpectedDiagnostics.size();
            if (numUnexpected == 1) {
                summaryBuilder.add("1 unexpected diagnostic was found:");
            } else {
                summaryBuilder.add(numUnexpected + " unexpected diagnostics were found:");
            }

            for (TestDiagnostic unexpected : unexpectedDiagnostics) {
                summaryBuilder.add("  " + unexpected.toString());
            }
        }

        if (!missingDiagnostics.isEmpty()) {
            int numMissing = missingDiagnostics.size();
            summaryBuilder.add(
                    StringsPlume.nplural(numMissing, "expected diagnostic")
                            + " "
                            + (numMissing == 1 ? "was" : "were")
                            + " not found:");

            for (TestDiagnostic missing : missingDiagnostics) {
                summaryBuilder.add("  " + missing.toString());
            }
        }

        // This used to call `TestUtilities.summarizeSourceFiles`. It may be nicer to move the logic
        // there.
        summaryBuilder.add(
                "While type-checking " + configuration.getTestSourceFiles().size() + " files.");

        return summaryBuilder.toString();
    }

    public static TypecheckResult fromCompilationResults(
            TestConfiguration configuration,
            CompilationResult result,
            List<TestDiagnostic> expectedDiagnostics) {

        Set<TestDiagnostic> actualDiagnostics =
                TestDiagnosticUtils.fromJavaxToolsDiagnosticList(result.getDiagnostics());

        Set<TestDiagnostic> unexpectedDiagnostics = new LinkedHashSet<>();
        unexpectedDiagnostics.addAll(actualDiagnostics);
        unexpectedDiagnostics.removeAll(expectedDiagnostics);

        List<TestDiagnostic> missingDiagnostics = new ArrayList<>(expectedDiagnostics);
        missingDiagnostics.removeAll(actualDiagnostics);

        return new TypecheckResult(
                configuration,
                result,
                expectedDiagnostics,
                missingDiagnostics,
                new ArrayList<>(unexpectedDiagnostics));
    }
}
