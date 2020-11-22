package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/** The Python {@code builtin_function_or_method} object. */
class PyJavaFunction implements PyObject {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("builtin_function_or_method",
                    PyJavaFunction.class, MethodHandles.lookup()));

    @Override
    public PyType getType() { return TYPE; }

    /** Description of the function to call */
    final MethodDef methodDef;

    /** Name of the containing module (or {@code null}). */
    final PyUnicode module;

    /**
     * Handle of Java method that implements the function or method. The
     * type of this handle is {@code (TUPLE, DICT) O}. It wraps
     * methodDef.meth with a check on the number of arguments and
     * manipulation to deliver them to this target.
     */
    final MethodHandle opCall;

    PyJavaFunction(MethodDef def, PyUnicode module) {
        this.methodDef = def;
        this.module = module;
        this.opCall = def.getOpCallHandle();
    }

    PyJavaFunction(MethodDef def) { this(def, null); }

    @Override
    public String toString() {
        return String.format("<built-in function %s>", methodDef.name);
    }

    // slot functions -------------------------------------------------

    @SuppressWarnings("unused")
    private PyObject __repr__() throws Throwable {
        return PyUnicode.fromFormat("<built-in function %s>",
                methodDef.name);
    }

    @SuppressWarnings("unused")
    private PyObject __call__(PyTuple args, PyDict kwargs)
            throws Throwable {
        try {
            return (PyObject) opCall.invokeExact(args, kwargs);
        } catch (MethodDef.BadCallException bce) {
            // After the BCE, check() should always throw.
            methodDef.check(args, kwargs);
            // It didn't :( so this is an internal error
            throw new InterpreterError(bce,
                    "Unexplained BadCallException in __call__");
        }
    }

}
