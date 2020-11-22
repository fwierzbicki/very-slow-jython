package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;

import uk.co.farowl.vsj2.evo4.PyType.Spec;

/**
 * The Python {@code code} object. (Compare {@code PyCodeObject} in
 * CPython's C API.) A {@code code} object describes the layout of a
 * {@link PyFrame}, and is a factory for frames of matching type.
 */
abstract class PyCode extends AbstractPyObject {

    /** The type {@code code}. */
    static final PyType TYPE = PyType.fromSpec(
            new Spec("code", PyCode.class, MethodHandles.lookup()));

    /** Characteristics of a PyCode (as CPython co_flags). */
    enum Trait {
        OPTIMIZED, NEWLOCALS, VARARGS, VARKEYWORDS, NESTED, GENERATOR,
        NOFREE, COROUTINE, ITERABLE_COROUTINE, ASYNC_GENERATOR
    }

    final EnumSet<Trait> traits;

    /** Number of positional arguments (not counting {@code *args}). */
    final int argcount;
    /** Number of positional-only arguments. */
    final int posonlyargcount;
    /** Number of keyword-only arguments. */
    final int kwonlyargcount;
    /** Number of local variables. */
    final int nlocals;
    /** Number of entries needed for evaluation stack. */
    final int stacksize;
    /** int expression of {@link #traits} compatible with CPython. */
    final int flags;
    /** First source line number. */
    final int firstlineno;
    /** Instruction opcodes, not {@code null}. */
    final PyBytes code;
    /** Constant objects needed by the code, not {@code null}. */
    final PyObject[] consts;
    /**
     * Names referenced in the code (elements guaranteed to be of type
     * {@code str}), not {@code null}.
     */
    final PyUnicode[] names;
    /**
     * Args and non-cell locals (elements guaranteed to be of type
     * {@code str}), not {@code null}.
     */
    final PyUnicode[] varnames;
    /**
     * Names referenced but not defined here (elements guaranteed to be
     * of type {@code str}), not {@code null}. These variables will be
     * set from the closure of the function.
     */
    final PyUnicode[] freevars;
    /**
     * Names defined here and referenced elsewhere (elements guaranteed
     * to be of type {@code str}), not {@code null}.
     */
    final PyUnicode[] cellvars;

    /* ---------------------- See CPython code.h ------------------ */
    /** Constant to be stored in {@link #cell2arg} as default. */
    static final int CELL_NOT_AN_ARG = -1;
    /** Maps cell indexes to corresponding arguments. */
    final int[] cell2arg;
    /** Where it was loaded from */
    final PyUnicode filename;
    /** Name of function etc. */
    final PyUnicode name;

    /** Encodes the address to/from line number mapping */
    final PyBytes lnotab;

    /* Masks for co_flags above */
    public static final int CO_OPTIMIZED = 0x0001;
    public static final int CO_NEWLOCALS = 0x0002;
    public static final int CO_VARARGS = 0x0004;
    public static final int CO_VARKEYWORDS = 0x0008;
    public static final int CO_NESTED = 0x0010;
    public static final int CO_GENERATOR = 0x0020;
    /*
     * The CO_NOFREE flag is set if there are no free or cell variables.
     * This information is redundant, but it allows a single flag test
     * to determine whether there is any extra work to be done when the
     * call frame it setup.
     */
    public static final int CO_NOFREE = 0x0040;

    /*
     * The CO_COROUTINE flag is set for coroutine functions (defined
     * with ``async def`` keywords)
     */
    public static final int CO_COROUTINE = 0x0080;
    public static final int CO_ITERABLE_COROUTINE = 0x0100;
    public static final int CO_ASYNC_GENERATOR = 0x0200;

    /**
     * Full constructor based on CPython's
     * {@code PyCode_NewWithPosOnlyArgs}. The {@link #traits} of the
     * code are supplied here as CPython reports them: as a bit array in
     * an integer, but the constructor makes a conversion, and it is the
     * {@link #traits} which should be used at the Java level.
     *
     * @param argcount value of {@link #argcount}
     * @param posonlyargcount value of {@link #posonlyargcount}
     * @param kwonlyargcount value of {@link #kwonlyargcount}
     * @param nlocals value of {@link #nlocals}
     * @param stacksize value of {@link #stacksize}
     * @param flags value of {@link #flags} and {@link #traits}
     * @param code value of {@link #code}
     * @param consts value of {@link #consts}
     * @param names value of {@link #names}
     * @param varnames value of {@link #varnames} must be {@code str}
     * @param freevars value of {@link #freevars} must be {@code str}
     * @param cellvars value of {@link #cellvars} must be {@code str}
     * @param filename value of {@link #filename} must be {@code str}
     * @param name value of {@link #name}
     * @param firstlineno value of {@link #firstlineno}
     * @param lnotab value of {@link #lnotab}
     */
    public PyCode( //
            int argcount,           // co_argcount
            int posonlyargcount,    // co_posonlyargcount
            int kwonlyargcount,     // co_kwonlyargcount
            int nlocals,            // co_nlocals
            int stacksize,          // co_stacksize
            int flags,              // co_flags

            PyBytes code,           // co_code

            PyTuple consts,         // co_consts

            PyTuple names,          // names ref'd in code
            PyTuple varnames,       // args and non-cell locals
            PyTuple freevars,       // ref'd here, def'd outer
            PyTuple cellvars,       // def'd here, ref'd nested

            PyUnicode filename,     // loaded from
            PyUnicode name,         // of function etc.
            int firstlineno,        // of source
            PyBytes lnotab          // map opcode address to source
    ) {
        super(TYPE);
        this.argcount = argcount;
        this.posonlyargcount = posonlyargcount;
        this.kwonlyargcount = kwonlyargcount;
        this.nlocals = nlocals;

        this.stacksize = stacksize;
        this.flags = flags;
        this.code = code;
        this.consts = consts.value;

        this.names = names(names);
        this.varnames = names(varnames);
        this.freevars = names(freevars);
        this.cellvars = names(cellvars);

        this.filename = filename;
        this.name = name;
        this.firstlineno = firstlineno;
        this.lnotab = lnotab;

        this.traits = traitsFrom(flags);
        if (varnames.size() != nlocals)
            throw new ValueError("code: varnames is too small");

        this.cell2arg = calcCell2arg();
    }

    // Attributes ------------------------------------------------

    /**
     * Get {@link #consts} as a {@code tuple}.
     * @return {@link #consts} as a {@code tuple}
     */
    PyTuple getConsts() { return PyTuple.wrap(consts); }

    /**
     * Get {@link #names} as a {@code tuple}.
     * @return {@link #names} as a {@code tuple}
     */
    PyTuple getNames() { return PyTuple.wrap(names); }

    /**
     * Get {@link #varnames} as a {@code tuple}.
     * @return {@link #varnames} as a {@code tuple}
     */
    PyTuple getVarnames() { return PyTuple.wrap(varnames); }

    /**
     * Get {@link #freevars} as a {@code tuple}.
     * @return {@link #freevars} as a {@code tuple}
     */
    PyTuple getFreevars() { return PyTuple.wrap(freevars); }

    /**
     * Get {@link #cellvars} as a {@code tuple}.
     * @return {@link #cellvars} as a {@code tuple}
     */
    PyTuple getCellvars() { return PyTuple.wrap(cellvars); }

    // Plumbing --------------------------------------------------

    /**
     * Create a {@code PyFrame} suitable to execute this {@code PyCode}
     * (adequate for module-level code).
     *
     * @param interpreter providing the module context
     * @param globals name space to treat as global variables
     * @param locals name space to treat as local variables
     * @return the frame
     */
    abstract PyFrame createFrame(Interpreter interpreter,
            PyDict globals, PyObject locals);

    /**
     * Create a {@code PyFrame} suitable to execute this {@code PyCode},
     * suitable for function invocation only after additional steps to
     * provide the arguments.
     *
     * @param interpreter providing the module context
     * @param globals name space to treat as global variables
     * @param closure an array of cells (may be {@code null})
     * @return the frame
     */
    abstract PyFrame createFrame(Interpreter interpreter,
            PyDict globals, PyCell[] closure);

    /**
     * Create a frame from CPython vector call arguments, in the simple
     * case where only positional arguments are required and are
     * available in exactly the required number. The initialisation of
     * the returned frame is complete.
     *
     * @param interpreter providing the module context
     * @param globals name space to treat as global variables
     * @param stack array containing {@link #argcount} arguments
     * @param start start position in that array
     * @return frame ready for evaluation
     */
    abstract PyFrame fastFrame(Interpreter interpreter, PyDict globals,
            PyObject[] stack, int start);

    /**
     * Check that all the objects in the tuple are {@code str}, and
     * return them as an array.
     */
    private static PyUnicode[] names(PyTuple tuple) {
        PyUnicode[] u = new PyUnicode[tuple.size()];
        int i = 0;
        for (PyObject name : tuple) {
            if (name instanceof PyUnicode)
                u[i++] = (PyUnicode) name;
            else
                throw new IllegalArgumentException(
                        String.format("Non-unicode name: {}", name));
        }
        return u;
    }

    /**
     * Create mapping between cells and arguments if needed. Helper for
     * constructor. Returns {@code null} if the mapping is not needed.
     */
    private int[] calcCell2arg() {
        // Return array (lazily created on first finding we need one)
        int[] cell2arg = null;
        int ncells = cellvars.length;
        if (ncells > 0) {
            // This many of the varnames are arguments
            int nargs = argcount + kwonlyargcount
                    + (traits.contains(Trait.VARARGS) ? 1 : 0)
                    + (traits.contains(Trait.VARKEYWORDS) ? 1 : 0);
            // For each cell name, see if it matches an argument
            for (int i = 0; i < ncells; i++) {
                PyUnicode cellName = cellvars[i];
                for (int j = 0; j < nargs; j++) {
                    PyUnicode argName = varnames[j];
                    if (cellName.equals(argName)) {
                        // A match: enter it in the cell2arg array
                        if (cell2arg == null) {
                            // In which case the array had better exist.
                            cell2arg = new int[ncells];
                            Arrays.fill(cell2arg, CELL_NOT_AN_ARG);
                        }
                        cell2arg[i] = j;
                        break;
                    }
                }
            }
        }
        return cell2arg;
    }

    /**
     * Convert a CPython-style {@link #flags} specifier to
     * {@link #traits}.
     */
    private static EnumSet<Trait> traitsFrom(int flags) {
        ArrayList<Trait> traits = new ArrayList<>();
        for (int m = 1; flags != 0; m <<= 1) {
            switch (m & flags) {
                case 0:
                    break; // When bit not set in flag.
                case CO_OPTIMIZED:
                    traits.add(Trait.OPTIMIZED);
                    break;
                case CO_NEWLOCALS:
                    traits.add(Trait.NEWLOCALS);
                    break;
                case CO_VARARGS:
                    traits.add(Trait.VARARGS);
                    break;
                case CO_VARKEYWORDS:
                    traits.add(Trait.VARKEYWORDS);
                    break;
                case CO_NESTED:
                    traits.add(Trait.NESTED);
                    break;
                case CO_GENERATOR:
                    traits.add(Trait.GENERATOR);
                    break;
                case CO_NOFREE:
                    traits.add(Trait.NOFREE);
                    break;
                case CO_COROUTINE:
                    traits.add(Trait.COROUTINE);
                    break;
                case CO_ITERABLE_COROUTINE:
                    traits.add(Trait.ITERABLE_COROUTINE);
                    break;
                case CO_ASYNC_GENERATOR:
                    traits.add(Trait.ASYNC_GENERATOR);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Undefined bit set in 'flags' argument");
            }
            // Ensure the bit we just tested is clear
            flags &= ~m;
        }
        return EnumSet.copyOf(traits);
    }

    // slot functions -------------------------------------------------

    @SuppressWarnings("unused")
    private PyObject __repr__() {
        return Py.str(String.format("<code %s %s>", name, traits));
    }

}
