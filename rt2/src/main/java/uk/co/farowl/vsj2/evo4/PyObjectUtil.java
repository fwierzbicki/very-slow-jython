package uk.co.farowl.vsj2.evo4;

import java.util.Map;
import java.util.StringJoiner;

/** Miscellaneous static helpers common to built-in objects. */
class PyObjectUtil {

    /**
     * Convert comparison result (int) to rich comparison result.
     * Typically, {@code u} is the result of
     * {@link Comparable#compareTo(Object)}.
     *
     * @param u comparison result
     * @param op kind of rich comparison requested
     * @return rich comparison result (Python {@code bool})
     */
    static PyObject richCompareHelper(int u, Comparison op) {
        boolean r = false;
        switch (op) {
            //@formatter:off
            case LE: r = u <= 0; break;
            case LT: r = u < 0; break;
            case EQ: r = u == 0; break;
            case NE: r = u != 0; break;
            case GE: r = u >= 0; break;
            case GT: r = u > 0; break;
            default: // pass
           //@formatter:on
        }
        return r ? PyBool.True : PyBool.False;
    }

    static PySequence repeat(PySequence self, PyObject n)
            throws TypeError, Throwable {
        if (Number.indexCheck(n)) {
            int count = Number.asSize(n, OverflowError::new);
            return self.repeat(count);
        } else {
            throw Abstract.typeError(CANT_MULTIPLY, n);
        }
    }

    private static final String CANT_MULTIPLY =
            "can't multiply sequence by non-int of type '%.200s'";

    /**
     * An implementation of {@code dict.__repr__} that may be applied to
     * any Java {@code Map} between {@code PyObject}s, in which kets and
     * values are represented as with {@code repr()}.
     *
     * @param map to be reproduced
     * @return a string like <code>{'a': 2, 'b': 3}</code>
     * @throws Throwable
     */
    static String
            mapRepr(Map<? extends PyObject, ? extends PyObject> map)
                    throws Throwable {
        StringJoiner sj = new StringJoiner(", ", "{", "}");
        for (Map.Entry<? extends PyObject, ? extends PyObject> e : map
                .entrySet()) {
            String key = Abstract.repr(e.getKey()).toString();
            String value = Abstract.repr(e.getValue()).toString();
            sj.add(key + ": " + value);
        }
        return sj.toString();
    }
}
