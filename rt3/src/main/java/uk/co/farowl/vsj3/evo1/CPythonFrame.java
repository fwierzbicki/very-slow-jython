package uk.co.farowl.vsj3.evo1;

import java.util.EnumSet;

import uk.co.farowl.vsj3.evo1.base.InterpreterError;

/** A {@link PyFrame} for executing CPython 3.8 byte code. */
class CPythonFrame extends PyFrame {

    /*
     * Translation note: NB: in a CPython frame all local storage
     * local:cell:free:valuestack is one array into which pointers are
     * set during frame construction. For CPython byte code in Java,
     * three arrays seems to suit.
     */

    /**
     * The concatenation of the cell and free variables (in that order).
     * We place these in a single array, and use the slightly confusing
     * CPython name, to maximise similarity with the CPython code for
     * opcodes LOAD_DEREF, STORE_DEREF, etc..
     * <p>
     * Non-local variables used in the current scope <b>and</b> a nested
     * scope are named in {@link PyCode#cellvars}. These come first.
     * <p>
     * Non-local variables used in the current scope or a nested scope,
     * <b>and</b> in an enclosing scope are named in
     * {@link PyCode#freevars}. During a call, these are provided in the
     * closure, copied to the end of this array.
     */
    final PyCell[] freevars;
    /** Simple local variables, named in {@link PyCode#varnames}. */
    final Object[] fastlocals;
    /** Value stack. */
    final Object[] valuestack;

    /** Index of first empty space on the value stack. */
    int stacktop = 0;

    /** Assigned eventually by return statement (or stays None). */
    Object returnValue = Py.None;

    /**
     * Create a {@code CPythonFrame}, which is a {@code PyFrame} with
     * the storage and mechanism to execute a module or isolated code
     * object (compiled to a {@link CPythonCode}.
     *
     * The caller specifies the local variables dictionary explicitly:
     * it may be the same as the {@code globals}.
     *
     * @param code that this frame executes
     * @param interpreter providing the module context
     * @param globals global name space
     * @param locals local name space
     */
    CPythonFrame(Interpreter interpreter, CPythonCode code,
            PyDict globals, Object locals) {
        super(interpreter, code, globals, locals);
        valuestack = new Object[code.stacksize];
        freevars = EMPTY_CELL_ARRAY;

        // The need for a dictionary of locals depends on the code
        EnumSet<PyCode.Trait> traits = code.traits;
        if (traits.contains(PyCode.Trait.NEWLOCALS)
                && traits.contains(PyCode.Trait.OPTIMIZED)) {
            fastlocals = new Object[code.nlocals];
        } else {
            fastlocals = null;
        }
    }

    @Override
    Object eval() {

        // Evaluation stack index
        int sp = this.stacktop;

        // Cached references from code
        PyUnicode[] names = code.names;
        Object[] consts = code.consts;
        char[] wordcode = code.wordcode;
        final int END = wordcode.length;

        /*
         * Opcode argument (where needed). See also case EXTENDED_ARG.
         * Every opcode that consumes oparg must set it to zero.
         */
        int oparg = 0;

        // Local variables used repeatedly in the loop
        Object v;
        PyUnicode name;

        loop: for (int ip = 0; ip < END; ip++) {

            // Pick up the next instruction
            int opword = wordcode[ip];

            try {
                // Interpret opcode
                switch (opword >> 8) {
                    // Cases ordered as CPython to aid comparison

                    case Opcode.NOP:
                        break;

                    case Opcode.LOAD_CONST:
                        oparg |= opword & 0xff;
                        v = consts[oparg];
                        valuestack[sp++] = v; // PUSH
                        oparg = 0;
                        break;

                    case Opcode.RETURN_VALUE:
                        returnValue = valuestack[--sp]; // POP
                        // ip = END; ?
                        break loop;

                    case Opcode.STORE_NAME:
                        oparg |= opword & 0xff;
                        name = names[oparg];
                        v = valuestack[--sp]; // POP
                        if (locals == null)
                            throw new SystemError(
                                    "no locals found when storing '%s'",
                                    name);
                        locals.put(name, v);
                        oparg = 0;
                        break;

                    case Opcode.LOAD_NAME:
                        oparg |= opword & 0xff;
                        name = names[oparg];

                        if (locals == null)
                            throw new SystemError(
                                    "no locals found when loading '%s'",
                                    name);
                        v = locals.get(name);

                        if (v == null) {
                            v = globals.get(name);
                            if (v == null) {
                                v = builtins.get(name);
                                if (v == null)
                                    throw new NameError(NAME_ERROR_MSG,
                                            name);
                            }
                        }
                        valuestack[sp++] = v; // PUSH
                        oparg = 0;
                        break;

                    case Opcode.EXTENDED_ARG:
                        /*
                         * This opcode extends the effective opcode
                         * argument of the next opcode that has one.
                         */
                        // before: ........xxxxxxxx00000000
                        // after : xxxxxxxxaaaaaaaa00000000
                        oparg = (oparg | opword & 0xff) << 8;
                        /*
                         * When we encounter an argument to a "real"
                         * opcode, we need only mask it to 8 bits and or
                         * it with the pre-positioned with oparg. Every
                         * opcode that consumes oparg must set it to
                         * zero.
                         */
                        break;

                    default:
                        throw new InterpreterError("ip: %d, opcode: %d",
                                ip - 1, opword >> 8);
                } // switch

            } catch (PyException pye) {
                /*
                 * We ought here to check for exception handlers
                 * (defined in Python and reflected in the byte code)
                 * potentially resuming the loop with ip at the handler
                 * code, or in a Python finally clause.
                 */
                // Should handle within Python, but for now, stop.
                System.err.println(pye);
                throw pye;
            } catch (InterpreterError ie) {
                /*
                 * An InterpreterError signals an internal error,
                 * recognised by our implementation: stop.
                 */
                throw ie;
            } catch (Throwable t) {
                /*
                 * A non-Python exception signals an internal error, in
                 * our implementation, in user-supplied Java, or from a
                 * Java library misused from Python.
                 */
                // Should handle within Python, but for now, stop.
                t.printStackTrace();
                throw new InterpreterError(t, "Non-PyException");
            }
        } // loop

        // ThreadState.get().swap(back);
        return returnValue;
    }

    // Supporting definitions and methods -----------------------------

    private static final PyCell[] EMPTY_CELL_ARRAY = PyCell.EMPTY_ARRAY;

    private static final String NAME_ERROR_MSG =
            "name '%.200s' is not defined";

}
