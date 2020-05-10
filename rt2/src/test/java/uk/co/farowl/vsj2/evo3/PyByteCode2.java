package uk.co.farowl.vsj2.evo3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A test illustrating a naive emulation using {@code MethodHandle} of
 * CPython's approach to type objects.
 * <p>
 * For simplicity, we use a byte code interpreter, implemented after
 * CPython's, but for just the opcodes we need.
 */
class PyByteCode2 {

    /**
     * A test that the method handles we place in nominally empty slots,
     * do in fact raise the exception used internally to detect them.
     */
    @SuppressWarnings("unused")
    @Test
    void testSlotsEmptyException() {

        // Call to handle that fills "empty" UNARY slot.
        PyObject v = new PyLong(100);
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r = (PyObject) Slot.Signature.UNARY.empty
                    .invokeExact(v);
        });

        // Call to handle that fills "empty" LEN slot.
        assertThrows(Slot.EmptyException.class, () -> { //
            int r = (int) Slot.Signature.LEN.empty.invokeExact(v);
        });

        // Call to handle that fills "empty" PREDICATE slot.
        assertThrows(Slot.EmptyException.class, () -> { //
            boolean r = (boolean) Slot.Signature.PREDICATE.empty
                    .invokeExact(v);
        });

        // Call to handle that fills "empty" BINARY slot.
        PyObject w = new PyLong(200);
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r = (PyObject) Slot.Signature.BINARY.empty
                    .invokeExact(v, w);
        });

        // Call to handle that fills "empty" SQ_ASSIGN slot.
        PyObject u = new PyTuple(v, w);
        assertThrows(Slot.EmptyException.class, new Executable() { //

            @Override
            public void execute() throws Throwable {
                Slot.Signature.SQ_ASSIGN.empty.invokeExact(u, 1, w);
            }
        });

        // Call to handle that fills "empty" RICHCMP slot.
        // Two PyObject argument call to "empty" slot.
        Comparison op = Comparison.LT;
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r = (PyObject) Slot.Signature.RICHCMP.empty
                    .invokeExact(v, w, op);
        });
    }

    /**
     * Test that tp_ slots accept only the right type of method handles.
     */
    @Test
    void testSlotTP() {
        // Create a type defining none of the reserved names
        final PyType basic = new PyType("0Test", PyObject.class);
        assertEquals(Slot.Signature.UNARY.empty, basic.tp_repr,
                "not EMPTY");

        // Make method handles to try
        final MethodHandle length =
                MethodHandles.empty(Slot.Signature.LEN.empty.type());
        final MethodHandle unary =
                MethodHandles.empty(Slot.Signature.UNARY.empty.type());
        final MethodHandle binary =
                MethodHandles.empty(Slot.Signature.BINARY.empty.type());
        final MethodHandle ternary = MethodHandles
                .empty(Slot.Signature.TERNARY.empty.type());

        // These go quietly
        Slot.tp_hash.setSlot(basic, length);
        Slot.tp_str.setSlot(basic, unary);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            Slot.tp_str.setSlot(basic, length);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.tp_hash.setSlot(basic, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.tp_hash.setSlot(basic, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.tp_hash.setSlot(basic, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.tp_hash.setSlot(basic, null);
        });

        // And the slots should be unaffected
        assertEquals(length, basic.tp_hash, "slot modified");
        assertEquals(unary, basic.tp_str, "slot modified");
    }

    /**
     * Test that nb_ slots accept only the right type of method handles.
     */
    @Test
    void testSlotNB() {
        // Create a type defining none of the reserved names
        final PyType number = new PyType("1Test", PyObject.class);
        assertEquals(Slot.Signature.UNARY.empty, number.nb_negative,
                Slot.nb_negative.name());
        assertEquals(Slot.Signature.BINARY.empty, number.nb_add,
                Slot.nb_add.name());

        // Make method handles to try
        final MethodHandle length =
                MethodHandles.empty(Slot.Signature.LEN.empty.type());
        final MethodHandle unary =
                MethodHandles.empty(Slot.Signature.UNARY.empty.type());
        final MethodHandle binary =
                MethodHandles.empty(Slot.Signature.BINARY.empty.type());
        final MethodHandle ternary = MethodHandles
                .empty(Slot.Signature.TERNARY.empty.type());
        // These go quietly
        Slot.nb_negative.setSlot(number, unary);
        Slot.nb_add.setSlot(number, binary);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            Slot.nb_negative.setSlot(number, length);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.nb_negative.setSlot(number, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.nb_negative.setSlot(number, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.nb_negative.setSlot(number, null);
        });

        assertThrows(InterpreterError.class, () -> { //
            Slot.nb_add.setSlot(number, length);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.nb_add.setSlot(number, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.nb_add.setSlot(number, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.nb_add.setSlot(number, null);
        });

        // And the slots should have the value set earlier
        assertEquals(unary, number.nb_negative, "slot modified");
        assertEquals(binary, number.nb_add, "slot modified");
    }

    /**
     * Test that sq_ slots accept only the right type of method handles.
     */
    @Test
    void testSlotSQ() {
        // Create a type defining none of the reserved names
        final PyType sequence = new PyType("2Test", PyObject.class);
        assertEquals(Slot.Signature.LEN.empty, sequence.sq_length,
                "not empty");

        // Make method handles to try
        final MethodHandle length =
                MethodHandles.empty(Slot.Signature.LEN.empty.type());
        final MethodHandle unary =
                MethodHandles.empty(Slot.Signature.UNARY.empty.type());
        final MethodHandle binary =
                MethodHandles.empty(Slot.Signature.BINARY.empty.type());
        final MethodHandle ternary = MethodHandles
                .empty(Slot.Signature.TERNARY.empty.type());

        // This goes quietly
        Slot.sq_length.setSlot(sequence, length);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            Slot.sq_length.setSlot(sequence, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.sq_length.setSlot(sequence, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.sq_length.setSlot(sequence, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.sq_length.setSlot(sequence, null);
        });

        // And the slot should be unaffected
        assertEquals(length, sequence.sq_length, "slot modified");
    }

    /**
     * Test that mp_ slots accept only the right type of method handles.
     */
    @Test
    void testSlotMP() {
        // Create a type defining none of the reserved names
        final PyType mapping = new PyType("3Test", PyObject.class);
        assertEquals(Slot.Signature.LEN.empty, mapping.mp_length,
                "not empty");

        // Make method handles to try
        MethodHandle length =
                MethodHandles.empty(Slot.Signature.LEN.empty.type());
        MethodHandle unary =
                MethodHandles.empty(Slot.Signature.UNARY.empty.type());
        MethodHandle binary =
                MethodHandles.empty(Slot.Signature.BINARY.empty.type());
        MethodHandle ternary = MethodHandles
                .empty(Slot.Signature.TERNARY.empty.type());

        // This goes quietly
        Slot.mp_length.setSlot(mapping, length);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            Slot.mp_length.setSlot(mapping, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.mp_length.setSlot(mapping, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.mp_length.setSlot(mapping, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.mp_length.setSlot(mapping, null);
        });

        // And the slot should be unaffected
        assertEquals(length, mapping.mp_length, "slot modified");
    }

    // --------------------- Generated Tests -----------------------
    // Code generated by py_byte_code2_evo3.py
    // from py_byte_code2.ex.py

    /**
     * Example 'load_store_name': <pre>
     * a = b
     * b = 4
     * c = 6
     * </pre>
     */
    //@formatter:off
    static final PyCode LOAD_STORE_NAME =
    /*
     *   1           0 LOAD_NAME                0 (b)
     *               2 STORE_NAME               1 (a)
     *
     *   2           4 LOAD_CONST               0 (4)
     *               6 STORE_NAME               0 (b)
     *
     *   3           8 LOAD_CONST               1 (6)
     *              10 STORE_NAME               2 (c)
     *              12 LOAD_CONST               2 (None)
     *              14 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 1, 64,
        Py.bytes(101, 0, 90, 1, 100, 0, 90, 0, 100, 1, 90, 2, 100, 2,
            83, 0),
        Py.tuple(Py.val(4), Py.val(6), Py.None),
        Py.tuple(Py.str("b"), Py.str("a"), Py.str("c")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("load_store_name"), Py.str("<module>"), 1,
        Py.bytes(4, 1, 4, 1));
    //@formatter:on

    @Test
    void test_load_store_name1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put("a", Py.val(1));
        globals.put("b", Py.val(2));
        Interpreter interp = Py.createInterpreter();
        interp.evalCode(LOAD_STORE_NAME, globals, globals);
        assertEquals(Py.val(2), globals.get("a"), "a == 2");
        assertEquals(Py.val(4), globals.get("b"), "b == 4");
        assertEquals(Py.val(6), globals.get("c"), "c == 6");
        //@formatter:on
    }

    /**
     * Example 'negate': <pre>
     * a, b = -a, -b
     * </pre>
     */
    //@formatter:off
    static final PyCode NEGATE =
    /*
     *   1           0 LOAD_NAME                0 (a)
     *               2 UNARY_NEGATIVE
     *               4 LOAD_NAME                1 (b)
     *               6 UNARY_NEGATIVE
     *               8 ROT_TWO
     *              10 STORE_NAME               0 (a)
     *              12 STORE_NAME               1 (b)
     *              14 LOAD_CONST               0 (None)
     *              16 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 2, 64,
        Py.bytes(101, 0, 11, 0, 101, 1, 11, 0, 2, 0, 90, 0, 90, 1,
            100, 0, 83, 0),
        Py.tuple(Py.None),
        Py.tuple(Py.str("a"), Py.str("b")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("negate"), Py.str("<module>"), 1,
        Py.bytes());
    //@formatter:on

    @Test
    void test_negate1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put("a", Py.val(6));
        globals.put("b", Py.val(-7));
        Interpreter interp = Py.createInterpreter();
        interp.evalCode(NEGATE, globals, globals);
        assertEquals(Py.val(-6), globals.get("a"), "a == -6");
        assertEquals(Py.val(7), globals.get("b"), "b == 7");
        //@formatter:on
    }

    @Test
    void test_negate2() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put("a", Py.val(6.0));
        globals.put("b", Py.val(-7.0));
        Interpreter interp = Py.createInterpreter();
        interp.evalCode(NEGATE, globals, globals);
        assertEquals(Py.val(-6.0), globals.get("a"), "a == -6.0");
        assertEquals(Py.val(7.0), globals.get("b"), "b == 7.0");
        //@formatter:on
    }

    /**
     * Example 'binary': <pre>
     * sum = a + b
     * diff = a - b
     * prod = a * b
     * </pre>
     */
    //@formatter:off
    static final PyCode BINARY =
    /*
     *   1           0 LOAD_NAME                0 (a)
     *               2 LOAD_NAME                1 (b)
     *               4 BINARY_ADD
     *               6 STORE_NAME               2 (sum)
     *
     *   2           8 LOAD_NAME                0 (a)
     *              10 LOAD_NAME                1 (b)
     *              12 BINARY_SUBTRACT
     *              14 STORE_NAME               3 (diff)
     *
     *   3          16 LOAD_NAME                0 (a)
     *              18 LOAD_NAME                1 (b)
     *              20 BINARY_MULTIPLY
     *              22 STORE_NAME               4 (prod)
     *              24 LOAD_CONST               0 (None)
     *              26 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 2, 64,
        Py.bytes(101, 0, 101, 1, 23, 0, 90, 2, 101, 0, 101, 1, 24, 0,
            90, 3, 101, 0, 101, 1, 20, 0, 90, 4, 100, 0, 83, 0),
        Py.tuple(Py.None),
        Py.tuple(Py.str("a"), Py.str("b"), Py.str("sum"),
            Py.str("diff"), Py.str("prod")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("binary"), Py.str("<module>"), 1,
        Py.bytes(8, 1, 8, 1));
    //@formatter:on

    @Test
    void test_binary1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put("a", Py.val(7));
        globals.put("b", Py.val(6));
        Interpreter interp = Py.createInterpreter();
        interp.evalCode(BINARY, globals, globals);
        assertEquals(Py.val(13), globals.get("sum"), "sum == 13");
        assertEquals(Py.val(1), globals.get("diff"), "diff == 1");
        assertEquals(Py.val(42), globals.get("prod"), "prod == 42");
        //@formatter:on
    }

    @Test
    void test_binary2() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put("a", Py.val(7.0));
        globals.put("b", Py.val(6.0));
        Interpreter interp = Py.createInterpreter();
        interp.evalCode(BINARY, globals, globals);
        assertEquals(Py.val(13.0), globals.get("sum"), "sum == 13.0");
        assertEquals(Py.val(1.0), globals.get("diff"), "diff == 1.0");
        assertEquals(Py.val(42.0), globals.get("prod"),
            "prod == 42.0");
        //@formatter:on
    }

    @Test
    void test_binary3() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put("a", Py.val(7.0));
        globals.put("b", Py.val(6));
        Interpreter interp = Py.createInterpreter();
        interp.evalCode(BINARY, globals, globals);
        assertEquals(Py.val(13.0), globals.get("sum"), "sum == 13.0");
        assertEquals(Py.val(1.0), globals.get("diff"), "diff == 1.0");
        assertEquals(Py.val(42.0), globals.get("prod"),
            "prod == 42.0");
        //@formatter:on
    }

    @Test
    void test_binary4() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put("a", Py.val(7));
        globals.put("b", Py.val(6.0));
        Interpreter interp = Py.createInterpreter();
        interp.evalCode(BINARY, globals, globals);
        assertEquals(Py.val(13.0), globals.get("sum"), "sum == 13.0");
        assertEquals(Py.val(1.0), globals.get("diff"), "diff == 1.0");
        assertEquals(Py.val(42.0), globals.get("prod"),
            "prod == 42.0");
        //@formatter:on
    }

}