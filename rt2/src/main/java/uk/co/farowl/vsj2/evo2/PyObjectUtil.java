package uk.co.farowl.vsj2.evo2;

/** Miscellaneous static helpers common to built-in objects. */
class PyObjectUtil {

    /** Helper to create an exception for internal type error. */
    static InterpreterError typeMismatch(PyObject v, PyType expected) {
        String fmt = "'%s' argument to slot where '%s' expected";
        return new InterpreterError(fmt, v.getType().name,
                expected.name);
    }

    static PyObject richCompareHelper(int u, Comparison op) {
        boolean r = false;
        switch (op) {
            case LE: r = u <= 0; break;
            case LT: r = u < 0; break;
            case EQ: r = u == 0; break;
            case NE: r = u != 0; break;
            case GE: r = u >= 0; break;
            case GT: r = u > 0; break;
            default: // pass
        }
        return r ? PyBool.True : PyBool.False;
    }
}
