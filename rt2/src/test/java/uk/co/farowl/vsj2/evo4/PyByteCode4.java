package uk.co.farowl.vsj2.evo4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Continues the test illustrating a naive emulation using
 * {@code MethodHandle} of CPython's approach to type objects. The
 * present tests focus on the {@link PyBool} object, which is the first
 * numeric to provide some challenges concerning inheritance.
 */
class PyByteCode4 {

    /** Test boolean "and" returns exactly the singleton True/False. */
    @Test
    void testBoolAnd() throws Throwable {
        final PyObject T = Py.True;
        final PyObject F = Py.False;
        // Assert exact same object, not just equal 'int'.
        assertSame(F, Number.and(F, F));
        assertSame(F, Number.and(F, T));
        assertSame(F, Number.and(T, F));
        assertSame(T, Number.and(T, T));
    }

    /** Test boolean "or" returns exactly the singleton True/False. */
    @Test
    void testBoolOr() throws Throwable {
        final PyObject T = Py.True;
        final PyObject F = Py.False;
        // Assert exact same object, not just equal 'int'.
        assertSame(F, Number.or(F, F));
        assertSame(T, Number.or(F, T));
        assertSame(T, Number.or(T, F));
        assertSame(T, Number.or(T, T));
    }

    /** Test boolean "xor" returns exactly the singleton True/False. */
    @Test
    void testBoolXor() throws Throwable {
        final PyObject T = Py.True;
        final PyObject F = Py.False;
        // Assert exact same object, not just equal 'int'.
        assertSame(F, Number.xor(F, F));
        assertSame(T, Number.xor(F, T));
        assertSame(T, Number.xor(T, F));
        assertSame(F, Number.xor(T, T));
    }

    // --------------------- Generated Tests -----------------------
    // Code generated by py_byte_code4_evo4.py
    // from py_byte_code4.ex.py

    /**
     * Example 'boolean_and': <pre>
     * a = u & False
     * b = u & True
     * c = 42 & u
     * d = 43 & u
     * </pre>
     */
    //@formatter:off
    static final PyCode BOOLEAN_AND =
    /*
     *   1           0 LOAD_NAME                0 (u)
     *               2 LOAD_CONST               0 (False)
     *               4 BINARY_AND
     *               6 STORE_NAME               1 (a)
     *
     *   2           8 LOAD_NAME                0 (u)
     *              10 LOAD_CONST               1 (True)
     *              12 BINARY_AND
     *              14 STORE_NAME               2 (b)
     *
     *   3          16 LOAD_CONST               2 (42)
     *              18 LOAD_NAME                0 (u)
     *              20 BINARY_AND
     *              22 STORE_NAME               3 (c)
     *
     *   4          24 LOAD_CONST               3 (43)
     *              26 LOAD_NAME                0 (u)
     *              28 BINARY_AND
     *              30 STORE_NAME               4 (d)
     *              32 LOAD_CONST               4 (None)
     *              34 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 2, 64,
        Py.bytes(101, 0, 100, 0, 64, 0, 90, 1, 101, 0, 100, 1, 64, 0,
            90, 2, 100, 2, 101, 0, 64, 0, 90, 3, 100, 3, 101, 0, 64,
            0, 90, 4, 100, 4, 83, 0),
        Py.tuple(Py.False, Py.True, Py.val(42), Py.val(43), Py.None),
        Py.tuple(Py.str("u"), Py.str("a"), Py.str("b"), Py.str("c"),
            Py.str("d")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("boolean_and"), Py.str("<module>"), 1,
        Py.bytes(8, 1, 8, 1, 8, 1));
    //@formatter:on

    @Test
    void test_boolean_and1() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u", Py.False);
        interp.evalCode(BOOLEAN_AND, globals, globals);
        assertEquals(Py.False, globals.get("a"), "a == False");
        assertEquals(Py.False, globals.get("b"), "b == False");
        assertEquals(Py.val(0), globals.get("c"), "c == 0");
        assertEquals(Py.val(0), globals.get("d"), "d == 0");
        //@formatter:on
    }

    @Test
    void test_boolean_and2() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u", Py.True);
        interp.evalCode(BOOLEAN_AND, globals, globals);
        assertEquals(Py.False, globals.get("a"), "a == False");
        assertEquals(Py.True, globals.get("b"), "b == True");
        assertEquals(Py.val(0), globals.get("c"), "c == 0");
        assertEquals(Py.val(1), globals.get("d"), "d == 1");
        //@formatter:on
    }

    @Test
    void test_boolean_and3() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u", Py.val(15));
        interp.evalCode(BOOLEAN_AND, globals, globals);
        assertEquals(Py.val(0), globals.get("a"), "a == 0");
        assertEquals(Py.val(1), globals.get("b"), "b == 1");
        assertEquals(Py.val(10), globals.get("c"), "c == 10");
        assertEquals(Py.val(11), globals.get("d"), "d == 11");
        //@formatter:on
    }

    /**
     * Example 'boolean_or': <pre>
     * a = u | False
     * b = u | True
     * c = 42 | u
     * d = 43 | u
     * </pre>
     */
    //@formatter:off
    static final PyCode BOOLEAN_OR =
    /*
     *   1           0 LOAD_NAME                0 (u)
     *               2 LOAD_CONST               0 (False)
     *               4 BINARY_OR
     *               6 STORE_NAME               1 (a)
     *
     *   2           8 LOAD_NAME                0 (u)
     *              10 LOAD_CONST               1 (True)
     *              12 BINARY_OR
     *              14 STORE_NAME               2 (b)
     *
     *   3          16 LOAD_CONST               2 (42)
     *              18 LOAD_NAME                0 (u)
     *              20 BINARY_OR
     *              22 STORE_NAME               3 (c)
     *
     *   4          24 LOAD_CONST               3 (43)
     *              26 LOAD_NAME                0 (u)
     *              28 BINARY_OR
     *              30 STORE_NAME               4 (d)
     *              32 LOAD_CONST               4 (None)
     *              34 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 2, 64,
        Py.bytes(101, 0, 100, 0, 66, 0, 90, 1, 101, 0, 100, 1, 66, 0,
            90, 2, 100, 2, 101, 0, 66, 0, 90, 3, 100, 3, 101, 0, 66,
            0, 90, 4, 100, 4, 83, 0),
        Py.tuple(Py.False, Py.True, Py.val(42), Py.val(43), Py.None),
        Py.tuple(Py.str("u"), Py.str("a"), Py.str("b"), Py.str("c"),
            Py.str("d")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("boolean_or"), Py.str("<module>"), 1,
        Py.bytes(8, 1, 8, 1, 8, 1));
    //@formatter:on

    @Test
    void test_boolean_or1() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u", Py.False);
        interp.evalCode(BOOLEAN_OR, globals, globals);
        assertEquals(Py.False, globals.get("a"), "a == False");
        assertEquals(Py.True, globals.get("b"), "b == True");
        assertEquals(Py.val(42), globals.get("c"), "c == 42");
        assertEquals(Py.val(43), globals.get("d"), "d == 43");
        //@formatter:on
    }

    @Test
    void test_boolean_or2() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u", Py.True);
        interp.evalCode(BOOLEAN_OR, globals, globals);
        assertEquals(Py.True, globals.get("a"), "a == True");
        assertEquals(Py.True, globals.get("b"), "b == True");
        assertEquals(Py.val(43), globals.get("c"), "c == 43");
        assertEquals(Py.val(43), globals.get("d"), "d == 43");
        //@formatter:on
    }

    @Test
    void test_boolean_or3() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u", Py.val(15));
        interp.evalCode(BOOLEAN_OR, globals, globals);
        assertEquals(Py.val(15), globals.get("a"), "a == 15");
        assertEquals(Py.val(15), globals.get("b"), "b == 15");
        assertEquals(Py.val(47), globals.get("c"), "c == 47");
        assertEquals(Py.val(47), globals.get("d"), "d == 47");
        //@formatter:on
    }

    /**
     * Example 'boolean_xor': <pre>
     * a = u ^ False
     * b = u ^ True
     * c = 42 ^ u
     * d = 43 ^ u
     * </pre>
     */
    //@formatter:off
    static final PyCode BOOLEAN_XOR =
    /*
     *   1           0 LOAD_NAME                0 (u)
     *               2 LOAD_CONST               0 (False)
     *               4 BINARY_XOR
     *               6 STORE_NAME               1 (a)
     *
     *   2           8 LOAD_NAME                0 (u)
     *              10 LOAD_CONST               1 (True)
     *              12 BINARY_XOR
     *              14 STORE_NAME               2 (b)
     *
     *   3          16 LOAD_CONST               2 (42)
     *              18 LOAD_NAME                0 (u)
     *              20 BINARY_XOR
     *              22 STORE_NAME               3 (c)
     *
     *   4          24 LOAD_CONST               3 (43)
     *              26 LOAD_NAME                0 (u)
     *              28 BINARY_XOR
     *              30 STORE_NAME               4 (d)
     *              32 LOAD_CONST               4 (None)
     *              34 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 2, 64,
        Py.bytes(101, 0, 100, 0, 65, 0, 90, 1, 101, 0, 100, 1, 65, 0,
            90, 2, 100, 2, 101, 0, 65, 0, 90, 3, 100, 3, 101, 0, 65,
            0, 90, 4, 100, 4, 83, 0),
        Py.tuple(Py.False, Py.True, Py.val(42), Py.val(43), Py.None),
        Py.tuple(Py.str("u"), Py.str("a"), Py.str("b"), Py.str("c"),
            Py.str("d")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("boolean_xor"), Py.str("<module>"), 1,
        Py.bytes(8, 1, 8, 1, 8, 1));
    //@formatter:on

    @Test
    void test_boolean_xor1() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u", Py.False);
        interp.evalCode(BOOLEAN_XOR, globals, globals);
        assertEquals(Py.False, globals.get("a"), "a == False");
        assertEquals(Py.True, globals.get("b"), "b == True");
        assertEquals(Py.val(42), globals.get("c"), "c == 42");
        assertEquals(Py.val(43), globals.get("d"), "d == 43");
        //@formatter:on
    }

    @Test
    void test_boolean_xor2() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u", Py.True);
        interp.evalCode(BOOLEAN_XOR, globals, globals);
        assertEquals(Py.True, globals.get("a"), "a == True");
        assertEquals(Py.False, globals.get("b"), "b == False");
        assertEquals(Py.val(43), globals.get("c"), "c == 43");
        assertEquals(Py.val(42), globals.get("d"), "d == 42");
        //@formatter:on
    }

    @Test
    void test_boolean_xor3() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u", Py.val(15));
        interp.evalCode(BOOLEAN_XOR, globals, globals);
        assertEquals(Py.val(15), globals.get("a"), "a == 15");
        assertEquals(Py.val(14), globals.get("b"), "b == 14");
        assertEquals(Py.val(37), globals.get("c"), "c == 37");
        assertEquals(Py.val(36), globals.get("d"), "d == 36");
        //@formatter:on
    }

}
