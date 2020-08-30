package uk.co.farowl.vsj2.evo4;

import java.math.BigInteger;

import uk.co.farowl.vsj2.evo4.Slot.EmptyException;

/** The Python {@code int} object. */
class PyLong implements PyObject {

    /** The type {@code int}. */
    static PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("int", PyLong.class));

    static PyLong ZERO = new PyLong(BigInteger.ZERO);
    static PyLong ONE = new PyLong(BigInteger.ONE);

    private final PyType type;
    final BigInteger value;

    /** Constructor for Python sub-class specifying {@link #type}. */
    PyLong(PyType type, BigInteger value) {
        this.type = type;
        this.value = value;
    }

    /** Construct a Python {@code long}. */
    PyLong(BigInteger value) { this(TYPE, value); }

    PyLong(long value) { this(BigInteger.valueOf(value)); }

    PyLong(PyLong value) { this(value.value); }

    @Override
    public PyType getType() { return type; }

    @Override
    public String toString() { return Py.defaultToString(this); }

    int asSize() {
        try {
            return value.intValueExact();
        } catch (ArithmeticException ae) {
            throw new OverflowError(INT_TOO_LARGE);
        }
    }

    private static String INT_TOO_LARGE =
            "Python int too large to convert to 'size'";

    /**
     * Value as a Java {@code double} using the round-half-to-even rule.
     *
     * @return nearest double
     * @throws OverflowError if out of double range
     */
    // Compare CPython longobject.c: PyLong_AsDouble
    double doubleValue() throws OverflowError {
        /*
         * BigInteger.doubleValue() rounds half-to-even as required, but
         * on overflow returns ±∞ rather than throwing.
         */
        double x = value.doubleValue();
        if (Double.isInfinite(x))
            throw new OverflowError(INT_TOO_LARGE_FLOAT);
        else
            return x;
    }

    private static String INT_TOO_LARGE_FLOAT =
            "Python int too large to convert to float";

    int signum() { return value.signum(); }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PyLong) {
            PyLong other = (PyLong) obj;
            return other.value.equals(this.value);
        } else
            return false;
    }

    // slot functions -------------------------------------------------

    static PyObject __new__(PyType type, PyTuple args, PyDict kwargs)
            throws Throwable {
        PyObject x = null, obase = null;
        int argsLen = args.size();
        switch (argsLen) {
            case 2:
                obase = args.get(1); // fall through
            case 1:
                x = args.get(0); // fall through
            case 0:
                break;
            default:
                throw new TypeError(
                        "int() takes at most %d arguments (%d given)",
                        2, argsLen);
        }

        // XXX This does not yet deal correctly with the type argument

        if (x == null) {
            // Zero-arg int() ... unless invalidly like int(base=10)
            if (obase != null) {
                throw new TypeError("int() missing string argument");
            }
            return ZERO;
        }

        if (obase == null)
            return Number.asLong(x);
        else {
            int base = Number.asSize(obase, null);
            if ((base != 0 && base < 2) || base > 36)
                throw new ValueError(
                        "int() base must be >= 2 and <= 36, or 0");
            else if (x instanceof PyUnicode)
                return new PyLong(new BigInteger(x.toString(), base));
            // else if ... support for bytes-like objects
            else
                throw new TypeError(NON_STR_EXPLICIT_BASE);
        }
    }

    private static final String NON_STR_EXPLICIT_BASE =
            "int() can't convert non-string with explicit base";

    static PyObject __repr__(PyLong v) {
        return Py.str(v.value.toString());
    }

    static PyObject __neg__(PyLong v) {
        return new PyLong(v.value.negate());
    }

    static PyObject __abs__(PyLong v) {
        return new PyLong(v.value.abs());
    }

    static PyObject __add__(PyLong v, PyObject w) {
        try {
            return new PyLong(v.value.add(valueOf(w)));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __radd__(PyLong w, PyObject v) {
        try {
            return new PyLong(valueOf(v).add(w.value));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __sub__(PyLong v, PyObject w) {
        try {
            return new PyLong(v.value.subtract(valueOf(w)));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __rsub__(PyLong w, PyObject v) {
        try {
            return new PyLong(valueOf(v).subtract(w.value));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __mul__(PyLong v, PyObject w) {
        try {
            return new PyLong(v.value.multiply(valueOf(w)));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __rmul__(PyLong w, PyObject v) {
        try {
            return new PyLong(valueOf(v).multiply(w.value));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __and__(PyLong v, PyObject w) {
        try {
            return new PyLong(v.value.and(valueOf(w)));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __rand__(PyLong w, PyObject v) {
        try {
            return new PyLong(valueOf(v).and(w.value));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __or__(PyLong v, PyObject w) {
        try {
            return new PyLong(v.value.or(valueOf(w)));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __ror__(PyLong w, PyObject v) {
        try {
            return new PyLong(valueOf(v).or(w.value));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __xor__(PyLong v, PyObject w) {
        try {
            return new PyLong(v.value.xor(valueOf(w)));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __rxor__(PyLong w, PyObject v) {
        try {
            return new PyLong(valueOf(v).xor(w.value));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __richcompare__(PyLong v, PyObject w,
            Comparison op) {
        if (w instanceof PyLong) {
            int u = v.value.compareTo(((PyLong) w).value);
            return PyObjectUtil.richCompareHelper(u, op);
        } else {
            return Py.NotImplemented;
        }
    }

    static boolean __bool__(PyLong v) {
        return !BigInteger.ZERO.equals(v.value);
    }

    static PyObject __index__(PyLong v) {
        if (v.getType() == TYPE)
            return v;
        else
            return new PyLong(v.value);
    }

    static PyObject __int__(PyLong v) { // identical to __index__
        if (v.getType() == TYPE)
            return v;
        else
            return new PyLong(v.value);
    }

    static PyObject __float__(PyLong v) { // return PyFloat
        return Py.val(v.doubleValue());
    }

    /**
     * Check the argument is a {@code PyLong} and return its value.
     *
     * @param v ought to be a {@code PyLong} (or sub-class)
     * @return the {@link #value} field of {@code v}
     * @throws ClassCastException if {@code v} is not compatible
     */
    private static BigInteger valueOf(PyObject v)
            throws ClassCastException {
        return ((PyLong) v).value;
    }

    /**
     * Convert the given object to a {@code PyLong} using the
     * {@code nb_int} slot, if available. Raise {@code TypeError} if
     * either the {@code nb_int} slot is not available or the result of
     * the call to {@code nb_int} returns something not of type
     * {@code int}.
     */
    // Compare CPython longobject.c::_PyLong_FromNbInt
    static PyObject fromNbInt(PyObject integral)
            throws TypeError, Throwable {
        PyType t = integral.getType();

        if ((t == PyLong.TYPE)) {
            // Fast path for the case that we already have an int.
            return integral;
        }

        else
            try {
                /*
                 * Convert using the nb_int slot, which should return
                 * something of exact type int.
                 */
                PyObject r = (PyObject) t.nb_int.invokeExact(integral);
                if (r.getType() == PyLong.TYPE) {
                    return r;
                } else if (r instanceof PyLong) {
                    // Result not of exact type int but is a subclass
                    Abstract.returnDeprecation("__int__", "int", r);
                    return r;
                } else
                    throw Abstract.returnTypeError("__int__", "int", r);
            } catch (EmptyException e) {
                // Slot __int__ is not defioned for t
                throw Abstract.requiredTypeError("an integer",
                        integral);
            }
    }

    /**
     * Convert the given object to a {@code PyLong} using the
     * {@code nb_index} or {@code nb_int} slots, if available (the
     * latter is deprecated). Raise {@code TypeError} if either
     * {@code nb_index} and {@code nb_int} slots are not available or
     * the result of the call to {@code nb_index} or {@code nb_int}
     * returns something not of type {@code int}. Should be replaced
     * with {@link Number#index(PyObject)} after the end of the
     * deprecation period.
     *
     * @throws Throwable
     */
    // Compare CPython longobject.c :: _PyLong_FromNbIndexOrNbInt
    static PyObject fromNbIndexOrNbInt(PyObject integral)
            throws Throwable {
        PyType t = integral.getType();

        if (t == PyLong.TYPE)
            // Fast path for the case that we already have an int.
            return integral;

        try {
            // Normally, the nb_index slot will do the job
            PyObject r = (PyObject) t.nb_index.invokeExact(integral);
            if (r.getType() == PyLong.TYPE)
                return r;
            else if (r instanceof PyLong) {
                // 'result' not of exact type int but is a subclass
                Abstract.returnDeprecation("__index__", "int", r);
                return r;
            } else
                throw Abstract.returnTypeError("__index__", "int", r);
        } catch (EmptyException e) {}

        // We're here because nb_index was empty. Try nb_int.
        if (Slot.nb_int.isDefinedFor(t)) {
            PyObject r = fromNbInt(integral);
            // ... but grumble about it.
            Warnings.format(DeprecationWarning.TYPE, 1,
                    "an integer is required (got type %.200s).  "
                            + "Implicit conversion to integers "
                            + "using __int__ is deprecated, and may be "
                            + "removed in a future version of Python.",
                    t.getName());
            return r;
        } else
            throw Abstract.requiredTypeError("an integer", integral);
    }

    /**
     * Convert a sequence of Unicode digits in the string u to a Python
     * integer value.
     */
    // Compare CPython longobject.c :: PyLong_FromUnicodeObject
    static PyLong fromUnicode(PyUnicode u, int base) {
        try {
            // XXX maybe check 2<=base<=36 even if Number.asLong does?
            return new PyLong(new BigInteger(u.toString(), base));
        } catch (NumberFormatException e) {
            throw new ValueError(
                    "invalid literal for int() with base %d: %.200s",
                    base, u);
        }
    }

    /**
     * Create a Python {@code int} from a Java {@code double}.
     *
     * @param value to convert
     * @return BigInteger equivalent.
     * @throws OverflowError when this is a floating infinity
     * @throws ValueError when this is a floating NaN
     */
    // Compare CPython longobject.c :: PyLong_FromDouble
    PyLong fromDouble(double value) {
        return new PyLong(PyFloat.bigIntegerFromDouble(value));
    }

}
