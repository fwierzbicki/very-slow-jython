package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test selected methods of {@link PyUnicode} on a variety of argument
 * types.
 */
@DisplayName("In PyUnicode")
class PyUnicodeTest extends UnitTestSupport {

    /** Base of tests that find strings in others. */
    abstract static class AbstractFindTest {
        /**
         * Provide a stream of examples as parameter sets to the tests
         * of methods that have "search" character, that is
         * {@code find}, {@code index}, {@code partition},
         * {@code count}, etc..
         *
         * @return the examples for search tests.
         */
        static Stream<Arguments> findExamples() {
            return Stream.of(//
                    findExample("pandemic", "pan"), //
                    findExample("pandemic", "mic"), //
                    findExample("abracadabra", "bra"), //
                    findExample("abracadabra", "a"), //
                    findExample("Bananaman", "ana"), //
                    findExample(GREEK, "λόγος"), //
                    findExample(GREEK, " "), //
                    findExample("画蛇添足 添足 添足", " 添"), //
                    /*
                     * The following contain non-BMP characters
                     * 🐍=U+1F40D and 🦓=U+1F993, each of which Python
                     * must consider to be a single character, but in
                     * the Java String realisation each is two chars.
                     */
                    // 🐍=\ud802\udc40, 🦓=\ud83e\udd93
                    findExample("One 🐍, a 🦓, two 🐍🐍.", "🐍",
                            new int[] {4, 16, 17}),
                    findExample("Left 🐍🦓🐍🦓: right.", "🐍🦓:",
                            new int[] {7}));
        }

        /**
         * Construct a search problem and reference result. This uses
         * Java {@code String.indexOf} for the reference answer, so it
         * will work correctly only for BMP strings. Where any SMP
         * characters are involved, call
         * {@link #findExample(String, String, int[], String)}.
         *
         * @param self to search
         * @param needle to search for
         * @return example data for a test
         */
        private static Arguments findExample(String self,
                String needle) {
            int[] indices = findIndices(self, needle);
            return findExample(self, needle, indices);
        }

        /**
         * Construct a search problem and reference result, where the
         * needle occurs at a list of indices.
         *
         * @param self to search
         * @param needle to search for
         * @param indices at which {@code needle}is found (code points)
         * @param pin to replace needle (if tested)
         * @return example data for a test
         */
        private static Arguments findExample(String self, String needle,
                int[] indices) {
            return arguments(self, needle, indices);
        }
    }

    /** Tests of {@code str.find} operating on the whole string. */
    @Nested
    @DisplayName("find (whole string)")
    class FindTest extends AbstractFindTest {

        @DisplayName("find(String, String, null, null)")
        @ParameterizedTest(name = "\"{0}\".find(\"{1}\")")
        @MethodSource("findExamples")
        void S_find_S(String s, String needle, int[] indices) {
            int r = PyUnicode.find(s, needle, null, null);
            if (indices.length == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[0]
                assertEquals(indices[0], r);
            }
        }

        @DisplayName("find(String, PyUnicode, null, null)")
        @ParameterizedTest(name = "\"{0}\".find(\"{1}\")")
        @MethodSource("findExamples")
        void S_find_U(String s, String needle, int[] indices) {
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            int r = PyUnicode.find(s, uNeedle, null, null);
            if (indices.length == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[0]
                assertEquals(indices[0], r);
            }
        }

        @DisplayName("find(PyUnicode, String, null, null)")
        @ParameterizedTest(name = "\"{0}\".find(\"{1}\")")
        @MethodSource("findExamples")
        void U_find_S(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            int r = u.find(needle, null, null);
            if (indices.length == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[0]
                assertEquals(indices[0], r);
            }
        }

        @DisplayName("find(PyUnicode, PyUnicode, null, null)")
        @ParameterizedTest(name = "\"{0}\".find(\"{1}\")")
        @MethodSource("findExamples")
        void U_find_U(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            int r = u.find(uNeedle, null, null);
            if (indices.length == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[0]
                assertEquals(indices[0], r);
            }
        }
    }

    /** Tests of {@code str.partition}. */
    @Nested
    @DisplayName("partition")
    class PartitionTest extends AbstractFindTest {

        @DisplayName("partition(String, String)")
        @ParameterizedTest(name = "\"{0}\".partition(\"{1}\")")
        @MethodSource("findExamples")
        void S_partition_S(String s, String needle, int[] indices) {
            PyTuple r = PyUnicode.partition(s, needle);
            assertPythonType(PyTuple.TYPE, r);
            assertEquals(3, r.size());
            for (int i = 0; i < 3; i++) {
                assertPythonType(PyUnicode.TYPE, r.get(i));
            }
            if (indices.length == 0) {
                // There should be no match
                assertEquals(Py.tuple(s, "", ""), r);
            } else {
                // Match at indices[0]
                int[] charIndices = toCharIndices(s, indices);
                // Work in char indices (so doubtful with surrogates)
                int n = charIndices[0], m = n + needle.length();
                assertEquals(Py.tuple(s.substring(0, n), needle,
                        s.substring(m)), r);
            }
        }

        @DisplayName("partition(String, PyUnicode)")
        @ParameterizedTest(name = "\"{0}\".partition(\"{1}\")")
        @MethodSource("findExamples")
        void S_partition_U(String s, String needle, int[] indices) {
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            PyTuple r = PyUnicode.partition(s, uNeedle);
            assertPythonType(PyTuple.TYPE, r);
            assertEquals(3, r.size());
            for (int i = 0; i < 3; i++) {
                assertPythonType(PyUnicode.TYPE, r.get(i));
            }
            if (indices.length == 0) {
                // There should be no match
                assertEquals(Py.tuple(s, "", ""), r);
            } else {
                // Match at indices[0]
                int[] charIndices = toCharIndices(s, indices);
                // Work in char indices (so doubtful with surrogates)
                int n = charIndices[0], m = n + needle.length();
                assertEquals(Py.tuple(s.substring(0, n), needle,
                        s.substring(m)), r);
            }
        }

        @DisplayName("partition(PyUnicode, String)")
        @ParameterizedTest(name = "\"{0}\".partition(\"{1}\")")
        @MethodSource("findExamples")
        void U_partition_S(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyTuple r = u.partition(needle);
            assertPythonType(PyTuple.TYPE, r);
            assertEquals(3, r.size());
            for (int i = 0; i < 3; i++) {
                assertPythonType(PyUnicode.TYPE, r.get(i));
            }
            if (indices.length == 0) {
                // There should be no match
                assertEquals(Py.tuple(s, "", ""), r);
            } else {
                // Match at indices[0]
                int[] charIndices = toCharIndices(s, indices);
                // Work in char indices (so doubtful with surrogates)
                int n = charIndices[0], m = n + needle.length();
                assertEquals(Py.tuple(s.substring(0, n), needle,
                        s.substring(m)), r);
            }
        }

        @DisplayName("partition(PyUnicode, PyUnicode)")
        @ParameterizedTest(name = "\"{0}\".partition(\"{1}\")")
        @MethodSource("findExamples")
        void U_partition_U(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            PyTuple r = u.partition(uNeedle);
            assertPythonType(PyTuple.TYPE, r);
            assertEquals(3, r.size());
            for (int i = 0; i < 3; i++) {
                assertPythonType(PyUnicode.TYPE, r.get(i));
            }
            if (indices.length == 0) {
                // There should be no match
                assertEquals(Py.tuple(s, "", ""), r);
            } else {
                // Match at indices[0]
                int[] charIndices = toCharIndices(s, indices);
                // Work in char indices (so doubtful with surrogates)
                int n = charIndices[0], m = n + needle.length();
                assertEquals(Py.tuple(s.substring(0, n), needle,
                        s.substring(m)), r);
            }
        }
    }

    /** Tests of {@code str.count} operating on the whole string. */
    @Nested
    @DisplayName("count (whole string)")
    class CountTest extends AbstractFindTest {

        @DisplayName("count(String, String, null, null)")
        @ParameterizedTest(name = "\"{0}\".count(\"{1}\")")
        @MethodSource("findExamples")
        void S_count_S(String s, String needle, int[] indices) {
            int r = PyUnicode.count(s, needle, null, null);
            assertEquals(indices.length, r);
        }

        @DisplayName("count(String, PyUnicode, null, null)")
        @ParameterizedTest(name = "\"{0}\".count(\"{1}\")")
        @MethodSource("findExamples")
        void S_count_U(String s, String needle, int[] indices) {
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            int r = PyUnicode.count(s, uNeedle, null, null);
            assertEquals(indices.length, r);
        }

        @DisplayName("count(PyUnicode, String, null, null)")
        @ParameterizedTest(name = "\"{0}\".count(\"{1}\")")
        @MethodSource("findExamples")
        void U_count_S(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            int r = u.count(needle, null, null);
            assertEquals(indices.length, r);
        }

        @DisplayName("count(PyUnicode, PyUnicode, null, null)")
        @ParameterizedTest(name = "\"{0}\".count(\"{1}\")")
        @MethodSource("findExamples")
        void U_count_U(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            int r = u.count(uNeedle, null, null);
            assertEquals(indices.length, r);
        }
    }

    /** Tests of {@code str.split} on an explicit separator. */
    @Nested
    @DisplayName("split on string")
    class SplitOnStringTest extends AbstractFindTest {

        @DisplayName("split(String, String)")
        @ParameterizedTest(name = "\"{0}\".split(\"{1}\")")
        @MethodSource("findExamples")
        void S_split_S(String s, String needle, int[] indices) {
            PyList r = PyUnicode.split(s, needle, -1);
            PyUnicode[] segments = expectedSplit(s, needle, indices);
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @DisplayName("split(String, PyUnicode)")
        @ParameterizedTest(name = "\"{0}\".split(\"{1}\")")
        @MethodSource("findExamples")
        void S_split_U(String s, String needle, int[] indices) {
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            PyList r = PyUnicode.split(s, uNeedle, -1);
            PyUnicode[] segments = expectedSplit(s, needle, indices);
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @DisplayName("split(PyUnicode, String)")
        @ParameterizedTest(name = "\"{0}\".split(\"{1}\")")
        @MethodSource("findExamples")
        void U_split_S(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyList r = u.split(needle, -1);
            PyUnicode[] segments = expectedSplit(s, needle, indices);
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @DisplayName("split(PyUnicode, PyUnicode)")
        @ParameterizedTest(name = "\"{0}\".split(\"{1}\")")
        @MethodSource("findExamples")
        void U_split_U(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            PyList r = u.split(uNeedle, -1);
            PyUnicode[] segments = expectedSplit(s, needle, indices);
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @Test
        @DisplayName("split(FOX, \"o\", 2)")
        void U_split_S_maxsplit() {
            PyUnicode u = newPyUnicode(FOX);
            PyList r = u.split("o", 2);
            PyUnicode[] segments = toPyUnicodeArray("The quick br",
                    "wn f", FOX.substring(FOX.indexOf("x")));
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @Test
        @DisplayName("split(FOX, \"o\", 0)")
        void U_split_S_maxsplit0() {
            PyUnicode u = newPyUnicode(FOX);
            PyList r = u.split("o", 0);
            PyUnicode[] segments = toPyUnicodeArray(FOX);
            assertEquals(1, r.size(), "number of segments");
            assertEquals(segments[0], r.get(0));
        }
    }

    /**
     * Base of tests that find strings in others (in reverse search).
     */
    abstract static class AbstractReverseFindTest {
        /**
         * Provide a stream of examples as parameter sets to the tests
         * of methods that have "search" character but scan in reverse,
         * that is {@code rfind}, {@code rindex}, {@code rpartition},
         * etc..
         *
         * @return the examples for search tests.
         */
        static Stream<Arguments> rfindExamples() {
            return Stream.of(//
                    rfindExample("pandemic", "pan"), //
                    rfindExample("pandemic", "mic"), //
                    rfindExample("abracadabra", "bra"), //
                    rfindExample("Bananaman", "ana"), //
                    rfindExample(GREEK, "λόγος"), //
                    rfindExample(GREEK, " "), //
                    rfindExample("画蛇添足 添足 添足", " 添"), //
                    /*
                     * The following contain non-BMP characters
                     * 🐍=U+1F40D and 🦓=U+1F993, each of which Python
                     * must consider to be a single character, but in
                     * the Java String realisation each is two chars.
                     */
                    // 🐍=\ud802\udc40, 🦓=\ud83e\udd93
                    rfindExample("One 🐍, a 🦓, two 🐍🐍.", "🐍",
                            new int[] {4, 16, 17}),
                    rfindExample("Left 🐍🦓🐍🦓: right.", "🐍🦓:",
                            new int[] {7}));
        }

        /**
         * Construct a search problem and reference result. This uses
         * Java {@code String.indexOf} for the reference answer, so it
         * will work correctly only for BMP strings. Where any SMP
         * characters are involved, call
         * {@link #rfindExample(String, String, int[], String)}.
         *
         * @param self to search
         * @param needle to search for
         * @return example data for a test
         */
        private static Arguments rfindExample(String self,
                String needle) {
            int[] indices = rfindIndices(self, needle);
            return rfindExample(self, needle, indices);
        }

        /**
         * Construct a search problem and reference result, where the
         * needle occurs at a list of indices.
         *
         * @param self to search
         * @param needle to search for
         * @param indices at which {@code needle}is found (code points)
         * @param pin to replace needle (if tested)
         * @return example data for a test
         */
        private static Arguments rfindExample(String self,
                String needle, int[] indices) {
            return arguments(self, needle, indices);
        }
    }

    /** Tests of {@code str.rfind} operating on the whole string. */
    @Nested
    @DisplayName("rfind (whole string)")
    class ReverseFindTest extends AbstractReverseFindTest {

        @DisplayName("rfind(String, String, null, null)")
        @ParameterizedTest(name = "\"{0}\".rfind(\"{1}\")")
        @MethodSource("rfindExamples")
        void S_rfind_S(String s, String needle, int[] indices) {
            int r = PyUnicode.rfind(s, needle, null, null);
            int M = indices.length;
            if (M == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[M-1]
                assertEquals(indices[M - 1], r);
            }
        }

        @DisplayName("rfind(String, PyUnicode, null, null)")
        @ParameterizedTest(name = "\"{0}\".rfind(\"{1}\")")
        @MethodSource("rfindExamples")
        void S_rfind_U(String s, String needle, int[] indices) {
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            int r = PyUnicode.rfind(s, uNeedle, null, null);
            int M = indices.length;
            if (M == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[M-1]
                assertEquals(indices[M - 1], r);
            }
        }

        @DisplayName("rfind(PyUnicode, String, null, null)")
        @ParameterizedTest(name = "\"{0}\".rfind(\"{1}\")")
        @MethodSource("rfindExamples")
        void U_rfind_S(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            int r = u.rfind(needle, null, null);
            int M = indices.length;
            if (M == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[M-1]
                assertEquals(indices[M - 1], r);
            }
        }

        @DisplayName("rfind(PyUnicode, PyUnicode, null, null)")
        @ParameterizedTest(name = "\"{0}\".rfind(\"{1}\")")
        @MethodSource("rfindExamples")
        void U_rfind_U(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            int r = u.rfind(uNeedle, null, null);
            int M = indices.length;
            if (M == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[M-1]
                assertEquals(indices[M - 1], r);
            }
        }
    }

    /** Tests of {@code str.rpartition}. */
    @Nested
    @DisplayName("rpartition")
    class ReversePartitionTest extends AbstractReverseFindTest {

        @DisplayName("rpartition(String, String)")
        @ParameterizedTest(name = "\"{0}\".rpartition(\"{1}\")")
        @MethodSource("rfindExamples")
        void S_rpartition_S(String s, String needle, int[] indices) {
            PyTuple r = PyUnicode.rpartition(s, needle);
            assertPythonType(PyTuple.TYPE, r);
            assertEquals(3, r.size());
            for (int i = 0; i < 3; i++) {
                assertPythonType(PyUnicode.TYPE, r.get(i));
            }
            int M = indices.length;
            if (M == 0) {
                // There should be no match
                assertEquals(Py.tuple(s, "", ""), r);
            } else {
                // Match at indices[M-1]
                int[] charIndices = toCharIndices(s, indices);
                // Work in char indices (so doubtful with surrogates)
                int n = charIndices[M - 1], m = n + needle.length();
                assertEquals(Py.tuple(s.substring(0, n), needle,
                        s.substring(m)), r);
            }
        }

        @DisplayName("rpartition(String, String)")
        @ParameterizedTest(name = "\"{0}\".rpartition(\"{1}\")")
        @MethodSource("rfindExamples")
        void S_rpartition_U(String s, String needle, int[] indices) {
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            PyTuple r = PyUnicode.rpartition(s, uNeedle);
            assertPythonType(PyTuple.TYPE, r);
            assertEquals(3, r.size());
            for (int i = 0; i < 3; i++) {
                assertPythonType(PyUnicode.TYPE, r.get(i));
            }
            int M = indices.length;
            if (M == 0) {
                // There should be no match
                assertEquals(Py.tuple(s, "", ""), r);
            } else {
                // Match at indices[M-1]
                int[] charIndices = toCharIndices(s, indices);
                // Work in char indices (so doubtful with surrogates)
                int n = charIndices[M - 1], m = n + needle.length();
                assertEquals(Py.tuple(s.substring(0, n), needle,
                        s.substring(m)), r);
            }
        }

        @DisplayName("rpartition(String, String)")
        @ParameterizedTest(name = "\"{0}\".rpartition(\"{1}\")")
        @MethodSource("rfindExamples")
        void U_rpartition_S(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyTuple r = u.rpartition(needle);
            assertPythonType(PyTuple.TYPE, r);
            assertEquals(3, r.size());
            for (int i = 0; i < 3; i++) {
                assertPythonType(PyUnicode.TYPE, r.get(i));
            }
            int M = indices.length;
            if (M == 0) {
                // There should be no match
                assertEquals(Py.tuple(s, "", ""), r);
            } else {
                // Match at indices[M-1]
                int[] charIndices = toCharIndices(s, indices);
                // Work in char indices (so doubtful with surrogates)
                int n = charIndices[M - 1], m = n + needle.length();
                assertEquals(Py.tuple(s.substring(0, n), needle,
                        s.substring(m)), r);
            }
        }

        @DisplayName("rpartition(String, String)")
        @ParameterizedTest(name = "\"{0}\".rpartition(\"{1}\")")
        @MethodSource("rfindExamples")
        void U_rpartition_U(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            PyTuple r = u.rpartition(uNeedle);
            assertPythonType(PyTuple.TYPE, r);
            assertEquals(3, r.size());
            for (int i = 0; i < 3; i++) {
                assertPythonType(PyUnicode.TYPE, r.get(i));
            }
            int M = indices.length;
            if (M == 0) {
                // There should be no match
                assertEquals(Py.tuple(s, "", ""), r);
            } else {
                // Match at indices[M-1]
                int[] charIndices = toCharIndices(s, indices);
                // Work in char indices (so doubtful with surrogates)
                int n = charIndices[M - 1], m = n + needle.length();
                assertEquals(Py.tuple(s.substring(0, n), needle,
                        s.substring(m)), r);
            }
        }
    }

    /** Tests of {@code str.rsplit} on an explicit separator. */
    @Nested
    @DisplayName("rsplit on string")
    class ReverseSplitOnStringTest extends AbstractReverseFindTest {

        @DisplayName("rsplit(String, String)")
        @ParameterizedTest(name = "\"{0}\".rsplit(\"{1}\")")
        @MethodSource("rfindExamples")
        void S_rsplit_S(String s, String needle, int[] indices) {
            PyList r = PyUnicode.rsplit(s, needle, -1);
            PyUnicode[] segments = expectedSplit(s, needle, indices);
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @DisplayName("rsplit(String, PyUnicode)")
        @ParameterizedTest(name = "\"{0}\".rsplit(\"{1}\")")
        @MethodSource("rfindExamples")
        void S_rsplit_U(String s, String needle, int[] indices) {
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            PyList r = PyUnicode.rsplit(s, uNeedle, -1);
            PyUnicode[] segments = expectedSplit(s, needle, indices);
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @DisplayName("rsplit(PyUnicode, String)")
        @ParameterizedTest(name = "\"{0}\".rsplit(\"{1}\")")
        @MethodSource("rfindExamples")
        void U_rsplit_S(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyList r = u.rsplit(needle, -1);
            PyUnicode[] segments = expectedSplit(s, needle, indices);
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @DisplayName("rsplit(PyUnicode, PyUnicode)")
        @ParameterizedTest(name = "\"{0}\".rsplit(\"{1}\")")
        @MethodSource("rfindExamples")
        void U_rsplit_U(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            PyList r = u.rsplit(uNeedle, -1);
            PyUnicode[] segments = expectedSplit(s, needle, indices);
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @Test
        @DisplayName("rsplit(FOX, \"o\", 2)")
        void U_rsplit_S_maxsplit() {
            PyUnicode u = newPyUnicode(FOX);
            PyList r = u.rsplit("o", 2);
            PyUnicode[] segments = toPyUnicodeArray(
                    FOX.substring(0, FOX.indexOf("over")),
                    "ver the lazy d", "g.");
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @Test
        @DisplayName("rsplit(FOX, \"o\", 0)")
        void U_rsplit_S_maxsplit0() {
            PyUnicode u = newPyUnicode(FOX);
            PyList r = u.rsplit("o", 0);
            PyUnicode[] segments = toPyUnicodeArray(FOX);
            assertEquals(1, r.size(), "number of segments");
            assertEquals(segments[0], r.get(0));
        }
    }

    /** Base of tests that exercise string replacement. */
    abstract static class AbstractReplaceTest {
        /**
         * Provide a stream of examples as parameter sets to the tests
         * of methods that have "search" character, that is .
         *
         * @return the examples for search tests.
         */
        static Stream<Arguments> replaceExamples() {
            return Stream.of(//
                    replaceExample("pandemic", "pan", "ping"), //
                    replaceExample("abracadabra", "bra", "x"), //
                    replaceExample("bananarama", "anar", " dr"), //
                    replaceExample("Σωκρατικὸς λόγος", "ὸς", "ὸι"), //
                    replaceExample("Σωκρατικὸς λόγος", "ς", "σ"), //
                    replaceExample("画蛇添足 添足 添足", " 添", "**"), //
                    /*
                     * The following contain non-BMP characters
                     * 🐍=U+1F40D and 🦓=U+1F993, each of which Python
                     * must consider to be a single character, but in
                     * the Java String realisation each is two chars.
                     */
                    // 🐍=\ud802\udc40, 🦓=\ud83e\udd93
                    replaceExample("One 🐍, a 🦓, two 🐍🐍.", "🐍",
                            new int[] {4, 16, 17}, "🦓"),
                    replaceExample("Swap 🐍🦓.", "🐍🦓", new int[] {5},
                            "(🦓🐍)"));
        }

        /**
         * Construct a search problem and reference result. This uses
         * Java {@code String.indexOf} for the reference answer, so it
         * will work correctly only for BMP strings. Where any SMP
         * characters are involved, call
         * {@link #replaceExample(String, String, int[], String)}.
         *
         * @param self to search
         * @param needle to search for
         * @param pin to replace needle
         * @return example data for a test
         */
        private static Arguments replaceExample(String self,
                String needle, String pin) {
            int[] indices = findIndices(self, needle);
            return replaceExample(self, needle, indices, pin);
        }

        /**
         * Construct a search problem and reference result, where the
         * needle occurs once.
         *
         * @param self to search
         * @param needle to search for
         * @param index at which {@code needle} may be found
         * @param pin to replace needle
         * @return example data for a test
         */
        private static Arguments replaceExample(String self,
                String needle, int index, String pin) {
            return replaceExample(self, needle, new int[] {index}, pin);
        }

        /**
         * Construct a search problem and reference result, where the
         * needle occurs at a list of indices.
         *
         * @param self to search
         * @param needle to search for
         * @param indices at which {@code needle}is found (code points)
         * @param pin to replace needle (if tested)
         * @return example data for a test
         */
        private static Arguments replaceExample(String self,
                String needle, int[] indices, String pin) {
            return arguments(self, needle, indices, pin);
        }

        /**
         * Return a list of strings equal to {@code s} with {@code 0} to
         * {@code M} replacements of the needle by the pin, guided by an
         * array of {@code M} char indices for the needle. Element zero
         * of the returned value is {@code s}.
         *
         * @param s in which to effect the replacements.
         * @param needle to replace
         * @param cpIndices array of {@code M} character indices
         * @param pin replacement string
         * @return {@code M+1} strings
         */
        static String[] replaceResults(String s, String needle,
                int[] cpIndices, String pin) {
            int[] charIndices = toCharIndices(s, cpIndices);
            final int M = charIndices.length, N = needle.length(),
                    P = pin.length();
            // Make a list of s with 0..M replacements at the indices
            List<String> results = new LinkedList<>();
            StringBuilder r = new StringBuilder(s);
            results.add(s);
            for (int m = 0; m < M; m++) {
                /*
                 * r contains s with m replacements, and its value has
                 * already been emitted to results. Compute the result
                 * of m+1 replacements. Start by trimming r at the
                 * (m+1)th needle.
                 */
                r.setLength(charIndices[m] + m * (P - N));
                // Now append the pin and the rest of s after the needle
                r.append(pin).append(s.substring(charIndices[m] + N));
                results.add(r.toString());
            }
            return results.toArray(new String[M + 1]);
        }

    }

    @Nested
    @DisplayName("replace")
    class ReplaceTest extends AbstractReplaceTest {

        @DisplayName("replace(String, String, String)")
        @ParameterizedTest(name = "\"{0}\".replace(\"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void S_replace_SS(String s, String needle, int[] indices,
                String pin) {
            Object r = PyUnicode.replace(s, needle, pin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        @DisplayName("replace(String, PyUnicode, String)")
        @ParameterizedTest(name = "\"{0}\".replace(\"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void S_replace_US(String s, String needle, int[] indices,
                String pin) {
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            Object r = PyUnicode.replace(s, uNeedle, pin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        @DisplayName("replace(String, String, PyUnicode)")
        @ParameterizedTest(name = "\"{0}\".replace(\"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void S_replace_SU(String s, String needle, int[] indices,
                String pin) {
            PyUnicode uPin = new PyUnicode(pin.codePoints().toArray());
            Object r = PyUnicode.replace(s, needle, uPin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        @DisplayName("replace(String, PyUnicode, PyUnicode)")
        @ParameterizedTest(name = "\"{0}\".replace(\"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void S_replace_UU(String s, String needle, int[] indices,
                String pin) {
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            PyUnicode uPin = new PyUnicode(pin.codePoints().toArray());
            Object r = PyUnicode.replace(s, uNeedle, uPin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        @DisplayName("PyUnicode.replace(String, String)")
        @ParameterizedTest(name = "\"{0}\".replace(\"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void U_replace_SS(String s, String needle, int[] indices,
                String pin) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            Object r = u.replace(needle, pin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        @DisplayName("PyUnicode.replace(PyUnicode, String)")
        @ParameterizedTest(name = "\"{0}\".replace(\"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void U_replace_US(String s, String needle, int[] indices,
                String pin) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            Object r = u.replace(uNeedle, pin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        @DisplayName("PyUnicode.replace(String, PyUnicode)")
        @ParameterizedTest(name = "\"{0}\".replace(\"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void U_replace_SU(String s, String needle, int[] indices,
                String pin) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uPin = new PyUnicode(pin.codePoints().toArray());
            Object r = u.replace(needle, uPin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        @DisplayName("PyUnicode.replace(PyUnicode, PyUnicode)")
        @ParameterizedTest(name = "\"{0}\".replace(\"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void U_replace_UU(String s, String needle, int[] indices,
                String pin) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            PyUnicode uPin = new PyUnicode(pin.codePoints().toArray());
            Object r = u.replace(uNeedle, uPin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        // Cases where simulation by Java String is too hard.
        // 🐍=\ud802\udc40, 🦓=\ud83e\udd93

        // @Test
        void surrogatePairNotSplit_SS() {
            // No high surrogate (D800-DBFF) accidental replacement
            String s = "🐍🐍", needle = "\ud83d", pin = "#";
            // Assert that Java gets the non-Pythonic answer
            assert s.replace(needle, pin).equals("#\udc0d#\udc0d");

            // Python does not match paired high surrogates as isolated
            Object r = PyUnicode.replace(s, needle, pin);
            assertEquals(s, r);

            // No low surrogate (DC00-DFFF) accidental replacement
            needle = "\udc0d";
            // Assert that Java gets the non-Pythonic answer
            assert s.replace(needle, pin).equals("\ud83d#\ud83d#");

            // Python does not match paired low surrogates as isolated
            r = PyUnicode.replace(s, needle, pin);
            assertEquals(s, r);
        }

        // @Test
        void surrogatePairNotSplit_US() {
            // No high surrogate (D800-DBFF) accidental replacement
            String s = "🐍🐍", pin = "#";
            PyUnicode needle = new PyUnicode(0xd83d);
            // Assert that Java gets the non-Pythonic answer
            assert s.replace(needle.toString(), pin)
                    .equals("#\udc0d#\udc0d");

            // Python does not match paired low surrogates as isolated
            Object r = PyUnicode.replace(s, needle, pin);
            assertEquals(s, r);

            // No low surrogate (DC00-DFFF) accidental replacement
            needle = new PyUnicode(0xdc0d);
            // Assert that Java gets the non-Pythonic answer
            assert s.replace(needle.toString(), pin)
                    .equals("\ud83d#\ud83d#");

            // Python does not match paired low surrogates as isolated
            r = PyUnicode.replace(s, needle, pin);
            assertEquals(s, r);
        }

        // @Test
        @DisplayName("🐍 is not dissected as \\ud802\\udc40")
        void supplementaryCharacterNotSplit_SS() {
            // No high surrogate (D800-DBFF) accidental replacement
            String s = "🐍🐍", needle = "\ud83d", pin = "#";
            // Assert that Java gets the non-Pythonic answer
            assert s.replace(needle, pin).equals("#\udc0d#\udc0d");

            // PyUnicode stores a surrogate pair as one character
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            assert u.equals(s);
            Object r = u.replace(needle, pin);
            assertEquals(u, r);

            // No low surrogate (DC00-DFFF) accidental replacement
            needle = "\udc0d";
            // Assert that Java gets the non-Pythonic answer
            assert s.replace(needle, pin).equals("\ud83d#\ud83d#");

            // PyUnicode stores a surrogate pair as one character
            r = u.replace(needle, pin);
            assertEquals(u, r);
        }

        // @Test
        @DisplayName("a 🦓 is not produced by String \\ud83e\\udd93")
        void S_noSpontaneousZebras() {
            // Deleting "-" risks surrogate pair formation
            String s = "\ud83e-\udd93\ud83e-\udd93", needle = "-";
            // Java String: nothing, bang, zebras
            assert s.replace(needle, "").equals("🦓🦓");

            // Python lone surrogates remain aloof even when adjacent
            PyUnicode e = new PyUnicode(0xd83e, 0xdd93, 0xd83e, 0xdd93);
            Object r = PyUnicode.replace(s, needle, "");
            assertEquals(e, r);
        }

        // @Test
        @DisplayName("a 🦓 is not produced by PyUnicode \\ud83e\\udd93")
        void U_noSpontaneousZebras_SS() {
            // No accidental surrogate pair formation
            String s = "\ud83e-\udd93\ud83e-\udd93", needle = "-";
            // Java String: nothing, bang, zebras
            assert s.replace(needle, "").equals("🦓🦓");

            // Python lone surrogates remain aloof even when adjacent
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            assert u.equals(s);
            PyUnicode e = new PyUnicode(0xd83e, 0xdd93, 0xd83e, 0xdd93);
            Object r = u.replace(needle, "");
            assertEquals(e, r);
        }
    }

    /** Base of tests that find and split on spaces. */
    abstract static class AbstractSplitAtSpaceTest {
        /**
         * Provide a stream of examples as parameter sets to the tests
         * of methods that search for runs of spaces, that is
         * {@code split} and {@code rsplit} with no sub-string given.
         *
         * @return the examples for search tests.
         */
        static Stream<Arguments> splitExamples() {
            return Stream.of(//
                    splitExample("cunning"), //
                    splitExample("the quick brown fox"), //
                    splitExample("\fthe\u000bquick\nbrown\u0085fox"), //
                    splitExample("\f the \u000b quick\n\r"
                            + " brown \u0085 fox\r\n"), //
                    splitExample(""), //
                    splitExample("\f\u000b\n\u0085"), //
                    splitExample(GREEK), //
                    splitExample("画蛇添足 添足 添足"), //
                    /*
                     * The following contain non-BMP characters
                     * 🐍=U+1F40D and 🦓=U+1F993, each of which Python
                     * must consider to be a single character, but in
                     * the Java String realisation each is two chars.
                     */
                    // 🐍=\ud802\udc40, 🦓=\ud83e\udd93
                    splitExample("One 🐍, a 🦓, two 🐍🐍."), //
                    splitExample("Left 🐍🦓🐍🦓: right.") //
            );
        }

        /**
         * Construct a search problem and reference result that is
         * segments that are separated in {@code s} by runs of space
         * characters.
         *
         * @param self to search
         * @return example data for a test
         */
        private static Arguments splitExample(String self) {
            return arguments(self, split(self));
        }

        /**
         * Return an array of segments that are separated in {@code s}
         * by runs of space characters. This uses Java {@code char}
         * tests and will work correctly for BMP strings, but would be
         * unreliable where any SMP space characters are involved. We
         * return these as {@link PyUnicode} to ensure that
         * {@code assertEquals} uses {@link PyUnicode#equals(Object)}
         * for comparison during tests.
         *
         * @param s string in question
         * @return the segments of {@code s}
         */
        private static PyUnicode[] split(String s) {
            LinkedList<String> segment = new LinkedList<>();
            int p = 0, start = 0, N = s.length();
            boolean text = false;
            while (true) {
                if (text) {
                    if (p >= N) {
                        segment.add(s.substring(start, p));
                        break;
                    } else if (isPythonSpace(s.charAt(p))) {
                        segment.add(s.substring(start, p));
                        text = false;
                    }
                } else {
                    if (p >= N) {
                        break;
                    } else if (!isPythonSpace(s.charAt(p))) {
                        start = p;
                        text = true;
                    }
                }
                p++;
            }
            return toPyUnicodeArray(
                    segment.toArray(new String[segment.size()]));
        }
    }

    /** Tests of {@code str.split} splitting at runs of spaces. */
    @Nested
    @DisplayName("split at spaces")
    class SplitAtSpaceTest extends AbstractSplitAtSpaceTest {

        @DisplayName("split(String)")
        @ParameterizedTest(name = "\"{0}\".split()")
        @MethodSource("splitExamples")
        void S_split_S(String s, PyUnicode[] segments) {
            PyList r = PyUnicode.split(s, null, -1);
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @DisplayName("split(PyUnicode)")
        @ParameterizedTest(name = "\"{0}\".split()")
        @MethodSource("splitExamples")
        void U_split_S(String s, PyUnicode[] segments) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyList r = u.split(null, -1);
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @Test
        @DisplayName("FOX.split(maxsplit=3)")
        void U_split_maxsplit() {
            PyUnicode u = newPyUnicode(FOX);
            PyList r = u.split(null, 3);
            PyUnicode[] segments = toPyUnicodeArray("The", "quick",
                    "brown", "fox jumps over the lazy dog.");
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @Test
        @DisplayName("split(FOX, 0)")
        void U_split_S_maxsplit0() {
            PyUnicode u = newPyUnicode(FOX);
            PyList r = u.split(null, 0);
            PyUnicode[] segments = toPyUnicodeArray(FOX);
            assertEquals(1, r.size(), "number of segments");
            assertEquals(segments[0], r.get(0));
        }
    }

    /** Tests of {@code str.rsplit} splitting at runs of spaces. */
    @Nested
    @DisplayName("rsplit at spaces")
    class ReverseSplitAtSpaceTest extends AbstractSplitAtSpaceTest {

        @DisplayName("rsplit(String)")
        @ParameterizedTest(name = "\"{0}\".rsplit()")
        @MethodSource("splitExamples")
        void S_split_S(String s, PyUnicode[] segments) {
            PyList r = PyUnicode.rsplit(s, null, -1);
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @DisplayName("rsplit(PyUnicode)")
        @ParameterizedTest(name = "\"{0}\".rsplit()")
        @MethodSource("splitExamples")
        void U_split_S(String s, PyUnicode[] segments) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyList r = u.rsplit(null, -1);
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @Test
        @DisplayName("FOX.rsplit(maxsplit=3)")
        void U_split_maxsplit() {
            PyUnicode u = newPyUnicode(FOX);
            PyList r = u.rsplit(null, 3);
            PyUnicode[] segments =
                    toPyUnicodeArray("The quick brown fox jumps over",
                            "the", "lazy", "dog.");
            assertEquals(segments.length, r.size(),
                    "number of segments");
            int i = 0;
            for (Object ri : r) { assertEquals(segments[i++], ri); }
        }

        @Test
        @DisplayName("rsplit(FOX, 0)")
        void U_rsplit_S_maxsplit0() {
            PyUnicode u = newPyUnicode(FOX);
            PyList r = u.split(null, 0);
            PyUnicode[] segments = toPyUnicodeArray(FOX);
            assertEquals(1, r.size(), "number of segments");
            assertEquals(segments[0], r.get(0));
        }
    }

    /** Base of tests that find and split line breaks. */
    abstract static class AbstractSplitlinesTest {
        /**
         * Provide a stream of examples as parameter sets to the tests
         * of {@code splitlines}.
         *
         * @return the examples for search tests.
         */
        static Stream<Arguments> splitExamples() {
            return Stream.of(//
                    splitExample("word", "word"), //
                    splitExample("Line1\nLine2", "Line1\n", "Line2"), //
                    splitExample("Line1\rLine2", "Line1\r", "Line2"), //
                    splitExample("Line1\r\nLine2", "Line1\r\n",
                            "Line2"), //
                    splitExample("Line1\n\rLine2", "Line1\n", "\r",
                            "Line2"), //
                    splitExample("\nLine1\nLine2\n", "\n", "Line1\n",
                            "Line2\n"), //
                    splitExample(NEWLINES, NEWLINES_SPLIT), //
                    splitExample("画蛇\u2029画蛇\u2028添足\u2029", "画蛇\u2029",
                            "画蛇\u2028", "添足\u2029"), //
                    /*
                     * The following contain non-BMP characters
                     * 🐍=U+1F40D and 🦓=U+1F993, each of which Python
                     * must consider to be a single character, but in
                     * the Java String realisation each is two chars.
                     */
                    // 🐍=\ud802\udc40, 🦓=\ud83e\udd93
                    splitExample("One 🐍\na 🦓,\ftwo 🐍🐍.", "One 🐍\n",
                            "a 🦓,\f", "two 🐍🐍."), //
                    splitExample("Left 🐍🦓\r🐍🦓: right.\r",
                            "Left 🐍🦓\r", "🐍🦓: right.\r") //
            );
        }

        /**
         * Construct a line-split problem and reference result that
         * includes the end-of-line characters.
         *
         * @param self to split
         * @param lines of the split (with ends kept)
         * @return example data for a test
         */
        private static Arguments splitExample(String self,
                String... lines) {
            return arguments(self, lines);
        }

        /**
         * Return a line with trailing end-of-line characters optionally
         * removed. We return this as {@link PyUnicode} to ensure that
         * {@code assertEquals} uses {@link PyUnicode#equals(Object)}
         * for comparison during tests.
         *
         * @param line string in question
         * @param keepend do not remove trailing end-of-lines
         * @return the {@code line} as {@code PyUnicode}
         */
        static PyUnicode refLine(String line, boolean keepend) {
            if (!keepend) {
                int n = line.length();
                if (line.endsWith("\r\n")) {
                    // Special case CR-LF.
                    line = line.substring(0, n - 2);
                } else if (n > 0) {
                    // Use Java definition. (any tweaks needed?)
                    char c = line.charAt(n - 1);
                    if (isPythonLineSeparator(c)) {
                        line = line.substring(0, n - 1);
                    }
                }
            }
            return newPyUnicode(line);
        }

        /**
         * Names of line separators followed by the separators
         * themselves. The exceptions are CR-LF and LF-CR sequences: the
         * first is one separator and the second is two (creating a
         * blank line).
         */
        private static final String NEWLINES =
                "LF\nVT\u000bFF\fCR\rFS\u001cGS\u001dRS\u001eNEL\u0085"
                        + "LSEP\u2028PSEP\u2029CR-LF\r\nLF-CR\n\rEND";
        private static String[] NEWLINES_SPLIT = {"LF\n", "VT\u000b",
                "FF\f", "CR\r", "FS\u001c", "GS\u001d", "RS\u001e",
                "NEL\u0085", "LSEP\u2028", "PSEP\u2029", "CR-LF\r\n",
                "LF-CR\n", "\r", "END"};

    }

    /** Tests of {@code str.splitlines} splitting at line breaks. */
    @Nested
    @DisplayName("split at line boundaries")
    class SplitlinesTest extends AbstractSplitlinesTest {

        @DisplayName("splitlines(String)")
        @ParameterizedTest(name = "\"{0}\".splitlines()")
        @MethodSource("splitExamples")
        void S_split_S(String s, String[] lines) {
            splitlinesTest(s, lines, false);
        }

        @DisplayName("splitlines(PyUnicode)")
        @ParameterizedTest(name = "\"{0}\".splitlines()")
        @MethodSource("splitExamples")
        void U_splitlines(String s, String[] lines) {
            splitlinesUnicodeTest(s, lines, false);
        }

        @DisplayName("splitlines(String) keepends=True")
        @ParameterizedTest(name = "\"{0}\".splitlines(True)")
        @MethodSource("splitExamples")
        void S_splitlines_keepends(String s, String[] lines) {
            splitlinesTest(s, lines, true);
        }

        @DisplayName("splitlines(PyUnicode) keepends=True")
        @ParameterizedTest(name = "\"{0}\".splitlines(True)")
        @MethodSource("splitExamples")
        void U_splitlines_keepends(String s, String[] lines) {
            splitlinesUnicodeTest(s, lines, true);
        }

        /** Call and check {@code str.splitlines} for PyUnicode */
        private void splitlinesTest(String s, String[] lines,
                boolean keepends) {
            PyList r = PyUnicode.splitlines(s, keepends);
            splitlinesCheck(lines, keepends, r);
        }

        /** Call and check {@code str.splitlines} for PyUnicode */
        private void splitlinesUnicodeTest(String s, String[] lines,
                boolean keepends) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyList r = u.splitlines(keepends);
            splitlinesCheck(lines, keepends, r);
        }

        /** Check the result of {@code str.splitlines} */
        private void splitlinesCheck(String[] lines, boolean keepends,
                PyList r) {
            assertEquals(lines.length, r.size(), "number of segments");
            int i = 0;
            for (Object ri : r) {
                assertEquals(refLine(lines[i++], keepends), ri);
            }
        }
    }

    /** Tests of predicate functions. */
    abstract static class PredicateTest {
        @Test
        void testIsascii() { fail("Not yet implemented"); }
    }

    // Support code ---------------------------------------------------

    /**
     * Return a list of char indices on {@code s} at which the given
     * {@code needle} may be found. Occurrences found are
     * non-overlapping. This uses Java {@code String.indexOf} and will
     * work correctly for BMP strings, but is unreliable where any SMP
     * characters are involved.
     *
     * @param s string in question
     * @param needle to search for
     * @return char indices at which {@code needle} may be found
     */
    static int[] findIndices(String s, String needle) {
        LinkedList<Integer> charIndices = new LinkedList<>();
        int n = needle.length(), p = 0;
        while ((p = s.indexOf(needle, p)) >= 0) {
            charIndices.add(p);
            p += n;
        }
        int[] a = new int[charIndices.size()];
        for (int i = 0; i < a.length; i++) { a[i] = charIndices.pop(); }
        return a;
    }

    /**
     * Return a list of char indices on {@code s} at which the given
     * {@code needle} may be found, working backwards from the end.
     * Although generated by a reverse scan, the return array is in
     * ascending order. This uses Java {@code String.indexOf} and will
     * work correctly for BMP strings, but is unreliable where any SMP
     * characters are involved.
     *
     * @param s string in question
     * @param needle to search for
     * @return char indices at which {@code needle} may be found
     */
    static int[] rfindIndices(String s, String needle) {
        LinkedList<Integer> charIndices = new LinkedList<>();
        int n = needle.length(), p = s.length() - n;
        while ((p = s.lastIndexOf(needle, p)) >= 0) {
            charIndices.push(p);
            p -= n;
        }
        int[] a = new int[charIndices.size()];
        for (Integer i = 0; i < a.length; i++) {
            a[i] = charIndices.pop();
        }
        return a;
    }

    /**
     * Return a list of char indices on {@code s} equivalent to the code
     * point indices supplied. Indices in the supplied array must be in
     * ascending order.
     *
     * @param s string in question
     * @param cpIndices code point indices to convert
     * @return equivalent char indices on s
     */
    static int[] toCharIndices(String s, int[] cpIndices) {
        final int M = cpIndices.length;
        int[] charIndices = new int[M];
        int cpi = 0, p = 0, m = 0;
        for (int cpIndex : cpIndices) {
            // Advance p to char index of next cp index
            while (cpi < cpIndex) {
                int cp = s.codePointAt(p);
                p += Character.isBmpCodePoint(cp) ? 1 : 2;
                cpi++;
            }
            charIndices[m++] = p;
        }
        return charIndices;
    }

    /**
     * Convert an argument list or array of String to an array of
     * {@link PyUnicode}.
     *
     * @param strings to convert
     * @return as {@code PyUnicode}
     */
    private static PyUnicode[] toPyUnicodeArray(String... strings) {
        int n = strings.length;
        PyUnicode[] r = new PyUnicode[n];
        for (int i = 0; i < n; i++) { r[i] = newPyUnicode(strings[i]); }
        return r;
    }

    /**
     * Take the {@code M} occurrences of the {@code needle} in
     * {@code s}, which are enumerated by ascending code point index in
     * {@code cpIndex}, and return an array of {@code M+1} segments
     * between the needles. We return {@link PyUnicode} so that
     * comparisons have Python semantics.
     *
     * @param s the reference string
     * @param needle identified (we use the length only)
     * @param cpIndices code point indices in {@code s}
     * @return the array of segments
     */
    private static PyUnicode[] expectedSplit(String s, String needle,
            int[] cpIndices) {
        int p = 0, i = 0, N = needle.length();
        int[] indices = toCharIndices(s, cpIndices);
        // One more segment than there are splits
        String[] segments = new String[indices.length + 1];
        for (int q : indices) {
            // needle at q ends the current segment
            segments[i++] = s.substring(p, q);
            // next segment is after the needle
            p = q + N;
        }
        // And the last segment is from after the last needle
        segments[i] = s.substring(p);
        return toPyUnicodeArray(segments);
    }

    /** Simple English string for ad hoc tests. */
    static final String FOX =
            "The quick brown fox jumps over the lazy dog.";

    /** Non-ascii precomposed polytonic Greek characters. */
    static final String GREEK = "Ἐν ἀρχῇ ἦν ὁ λόγος, " //
            + "καὶ ὁ λόγος ἦν πρὸς τὸν θεόν, " //
            + "καὶ θεὸς ἦν ὁ λόγος.";

    /**
     * Define what characters are to be treated as a space according to
     * Python 3.
     */
    private static boolean isPythonSpace(char ch) {
        // Use the Java built-in methods as far as possible
        return Character.isWhitespace(ch) // ASCII spaces and some
                // remaining Unicode spaces
                || Character.isSpaceChar(ch)
                // NEXT LINE (not a space in Java or Unicode)
                || ch == 0x0085;
    }

    /**
     * Define what characters are to be treated as a line separator
     * according to Python 3.
     */
    private static boolean isPythonLineSeparator(char c) {
        return c == '\n' || c == '\r' || c == 0xb || c == '\f'
                || c == 0x1c || c == 0x1d || c == 0x1e || c == 0x85
                || c == 0x2028 || c == 0x2029;
    }
}
