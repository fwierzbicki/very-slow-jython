package uk.co.farowl.vsj2.evo3;

import java.math.BigInteger;

/** The Python {@code int} object. */
class PyLong implements PyObject {

    static PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("int", PyLong.class)
            );

    @Override
    public PyType getType() { return TYPE; }
    final BigInteger value;

    PyLong(BigInteger value) { this.value = value; }

    PyLong(long value) { this.value = BigInteger.valueOf(value); }

    @Override
    public String toString() { return value.toString(); }

    int asSize() {
        try {
            return value.intValueExact();
        } catch (ArithmeticException ae) {
            throw new OverflowError(INT_TOO_LARGE);
        }
    }
    private static String INT_TOO_LARGE =
            "Python int too large to convert to 'size'";

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

    static PyObject neg(PyLong v) {
        return new PyLong(v.value.negate());
    }

    static PyObject nb_absolute(PyLong v) {
        return new PyLong(v.value.abs());
    }

    static PyObject add(PyObject v, PyObject w) {
        try {
            BigInteger a = valueOf(v);
            BigInteger b = valueOf(w);
            return new PyLong(a.add(b));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject sub(PyObject v, PyObject w) {
        try {
            BigInteger a = valueOf(v);
            BigInteger b = valueOf(w);
            return new PyLong(a.subtract(b));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject mul(PyObject v, PyObject w) {
        try {
            BigInteger a = valueOf(v);
            BigInteger b = valueOf(w);
            return new PyLong(a.multiply(b));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject and(PyObject v, PyObject w) {
        try {
            BigInteger a = valueOf(v);
            BigInteger b = valueOf(w);
            return new PyLong(a.and(b));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject or(PyObject v, PyObject w) {
        try {
            BigInteger a = valueOf(v);
            BigInteger b = valueOf(w);
            return new PyLong(a.or(b));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject xor(PyObject v, PyObject w) {
        try {
            BigInteger a = valueOf(v);
            BigInteger b = valueOf(w);
            return new PyLong(a.xor(b));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject tp_richcompare(PyLong v, PyObject w, Comparison op) {
        if (w instanceof PyLong) {
            int u = v.value.compareTo(((PyLong) w).value);
            return PyObjectUtil.richCompareHelper(u, op);
        } else {
            return Py.NotImplemented;
        }
    }

    static boolean nb_bool(PyLong v) {
        return !BigInteger.ZERO.equals(v.value);
    }

    static PyObject nb_index(PyLong v) {
        if (v.getType() == TYPE)
            return v;
        else
            return new PyLong(v.value);
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
}
