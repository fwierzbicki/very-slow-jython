// Copyright (c)2021 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import uk.co.farowl.vsj3.evo1.Exposed.Default;
import uk.co.farowl.vsj3.evo1.Exposed.Name;
import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;
import uk.co.farowl.vsj3.evo1.PyObjectUtil.NoConversion;
import uk.co.farowl.vsj3.evo1.PySequence.Delegate;
import uk.co.farowl.vsj3.evo1.PySlice.Indices;
import uk.co.farowl.vsj3.evo1.base.InterpreterError;

/**
 * The Python {@code str} object is implemented by both
 * {@code PyUnicode} and {@code String}. Most strings used as names
 * (keys) and text is quite satisfactorily represented by Java
 * {@code String}. All operations will produce the same result for
 * Python, whichever representation is used.
 * <p>
 * Java {@code String}s are compact, but where they contain non-BMP
 * characters, these are represented by a pair of code units, which
 * makes certain operations (such as indexing) expensive, but not those
 * involving sequential access. By contrast, a {@code PyUnicode} is
 * time-efficient, but each character occupies one {@code int}.
 */
class PyUnicode implements CraftedPyObject, PyDict.Key {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("str", MethodHandles.lookup())
                    .methods(PyUnicodeMethods.class)
                    .adopt(String.class));
    protected PyType type;

    /**
     * The implementation holds a Java {@code int} array of code points.
     */
    private final int[] value;

    /**
     * Helper to implement {@code __getitem__} and other index-related
     * operations.
     */
    private UnicodeDelegate delegate = new UnicodeDelegate();

    /**
     * Cached hash of the {@code str}, lazily computed in
     * {@link #hashCode()}. Zero if unknown, and nearly always unknown
     * if zero.
     */
    private int hash;

    /**
     * Construct an instance of {@code PyUnicode}, a {@code str} or a
     * sub-class, from a given array of code points, with the option to
     * re-use that array as the implementation. If the actual array is
     * is re-used the caller must give up ownership and never modify it
     * after the call. See {@link #fromCodePoint(int)} for a correct
     * use.
     *
     * @param type actual type the instance should have
     * @param iPromiseNotToModify if {@code true}, the array becomes the
     *     implementation array, otherwise the constructor takes a copy.
     * @param codePoints the array of code points
     */
    private PyUnicode(PyType type, boolean iPromiseNotToModify,
            int[] codePoints) {
        this.type = type;
        if (iPromiseNotToModify)
            this.value = codePoints;
        else
            this.value = Arrays.copyOf(codePoints, codePoints.length);
    }

    /**
     * Construct an instance of {@code PyUnicode}, a {@code str} or a
     * sub-class, from a given array of code points. The constructor
     * takes a copy.
     *
     * @param type actual type the instance should have
     * @param codePoints the array of code points
     */
    protected PyUnicode(PyType type, int[] codePoints) {
        this(type, false, codePoints);
    }

    /**
     * Construct an instance of {@code PyUnicode}, a {@code str} or a
     * sub-class, from a given Java {@code String}. The constructor
     * interprets surrogate pairs as defining one code point. Lone
     * surrogates are preserved (e.g. for byte smuggling).
     *
     * @param type actual type the instance should have
     * @param value to have
     */
    protected PyUnicode(PyType type, String value) {
        this(TYPE, value.codePoints().toArray());
    }

    // Factory methods ------------------------------------------------
    // These may return a Java String or a PyUnicode

    /**
     * Return a Python {@code str} representing the single character
     * with the given code point. The return may be a Java
     * {@code String} (for BMP code points) or a {@code PyUnicode}.
     *
     * @param cp to code point convert
     * @return a Python {@code str}
     */
    public static Object fromCodePoint(int cp) {
        // We really need to know how the string will be used :(
        if (Character.charCount(cp) == 1)
            return Character.toString((char)cp);
        else
            return new PyUnicode(TYPE, true, new int[] {cp});
    }

    /**
     * Return a Python {@code str} representing the same sequence of
     * characters as the given Java {@code String}, but as a PyUnicode
     * if it contains non-BMP code points.
     *
     * @param cp to code point convert
     * @return a Python {@code str}
     */
    public static Object fromJavaString(String s) {
        if (isBMP(s))
            return s;
        else
            return new PyUnicode(TYPE, true, s.codePoints().toArray());
    }

    /**
     * Test whether a string contains no characters above the BMP range,
     * that is, any characters that require surrogate pairs to represent
     * them. The method returns {@code true} if and only if the string
     * consists entirely of BMP characters or is empty.
     *
     * @param s the string to test
     * @return whether contains no non-BMP characters
     */
    private static boolean isBMP(String s) {
        return s.codePoints().dropWhile(Character::isBmpCodePoint)
                .findFirst().isEmpty();
    }

    // Special methods ------------------------------------------------

    @SuppressWarnings("unused")
    private int __len__() { return value.length; }

    @SuppressWarnings("unused")
    private static int __len__(String self) {
        return self.codePointCount(0, self.length());
    }

    @SuppressWarnings("unused")
    private Object __str__() { return this; }

    @SuppressWarnings("unused")
    private static Object __str__(String self) { return self; }

    private static Object __repr__(String self) {
        // Ok, it should be more complicated but I'm in a hurry.
        return "'" + self + "'";
    }

    @SuppressWarnings("unused")
    private Object __repr__() {
        StringBuilder b = new StringBuilder();
        for (int c : delegate) { b.appendCodePoint(c); }
        return __repr__(b.toString());
    }

    /**
     * The hash of a {@link PyUnicode} is the same as that of a Java
     * {@code String} equal to it. This is so that a given Python
     * {@code str} may be found as a match in hashed data structures,
     * whichever representation is used for the key or query.
     */
    @SuppressWarnings("unused")
    private int __hash__() {
        // Reproduce on value the hash defined for java.lang.String
        if (hash == 0 && value.length > 0) {
            int h = 0;
            for (int c : value) {
                if (Character.isBmpCodePoint(c)) {
                    // c is represented by itself in a String
                    h = h * 31 + c;
                } else {
                    // c would be represented in a Java String by:
                    int hi = (c >>> 10) + HIGH_SURROGATE_OFFSET;
                    int lo = (c & 0x3ff) + Character.MIN_LOW_SURROGATE;
                    h = (h * 31 + hi) * 31 + lo;
                }
            }
            hash = h;
        }
        return hash;
    }

    @SuppressWarnings("unused")
    private static int __hash__(String self) { return self.hashCode(); }

    @SuppressWarnings("unused")
    private Object __add__(Object w) throws Throwable {
        return delegate.__add__(w);
    }

    @SuppressWarnings("unused")
    private static Object __add__(String v, Object w) throws Throwable {
        return adapt(v).__add__(w);
    }

    private Object __mul__(Object n) throws Throwable {
        return delegate.__mul__(n);
    }

    private static Object __mul__(String self, Object n)
            throws Throwable {
        return adapt(self).__mul__(n);
    }

    @SuppressWarnings("unused")
    private Object __rmul__(Object n) throws Throwable {
        return __mul__(n);
    }

    @SuppressWarnings("unused")
    private static Object __rmul__(String self, Object n)
            throws Throwable {
        return __mul__(self, n);
    }

    @SuppressWarnings("unused")
    private Object __getitem__(Object item) throws Throwable {
        return delegate.__getitem__(item);
    }

    @SuppressWarnings("unused")
    private static Object __getitem__(String self, Object item)
            throws Throwable {
        StringAdapter delegate = adapt(self);
        return delegate.__getitem__(item);
    }

    // Methods --------------------------------------------------------

    @PythonMethod(primary = false)
    boolean isascii() {
        for (int c : delegate) { if (c > 127) { return false; } }
        return true;
    }

    @PythonMethod
    static boolean isascii(String self) {
        for (int c : adapt(self)) { if (c > 127) { return false; } }
        return true;
    }

    @PythonMethod
    PyUnicode ljust(int width, @Default(" ") String fillchar) {
        int len = Math.min(fillchar.length(), 4);
        if (fillchar.codePointCount(0, len) != 1) {
            throw new TypeError(BAD_FILLCHAR);
        }
        int n = value.length;
        int m = Math.max(width, n), start = 0, fill = m - n;
        if (fill <= 0) { return this; }
        int[] buf = new int[m];
        // The original
        System.arraycopy(value, start, buf, 0, n);
        // The fill
        int fillcp = fillchar.codePointAt(0);
        for (int i = n; i < m; i++) { buf[i] = fillcp; }
        return new PyUnicode(TYPE, buf);
    }

    @PythonMethod(primary = false)
    static String ljust(String self, int width, String fillchar) {
        int len = Math.min(fillchar.length(), 4);
        if (fillchar.codePointCount(0, len) != 1) {
            throw new TypeError(BAD_FILLCHAR);
        }
        int n = self.codePointCount(0, self.length());
        int m = Math.max(width, n), start = 0, fill = m - n;
        if (fill <= 0) { return self; }
        StringBuilder buf =
                new StringBuilder(self.length() + fill * len);
        // The original
        buf.append(self.substring(start));
        // The fill
        for (int i = 0; i < fill; i++) { buf.append(fillchar); }
        return buf.toString();

    }

    private static String BAD_FILLCHAR =
            "the fill character must be exactly one character long";

    // Simplified version (no count)
    @PythonMethod
    String replace(Object old, @Name("new") Object _new) {
        // Annotations repeat since cannot rely on order processed
        String oldstr = old.toString();
        String newstr = _new.toString();
        return this.toString().replace(oldstr, newstr);

    }

    @PythonMethod(primary = false)
    static String replace(String self, Object old, Object _new) {
        // Annotations repeat since cannot rely on order processed
        String oldstr = old.toString();
        String newstr = _new.toString();
        return self.replace(oldstr, newstr);
    }

    @PythonMethod
    PyUnicode zfill(int width) {
        int n = value.length;
        int m = Math.max(width, n), start = 0, fill = m - n, c;
        if (fill <= 0) { return this; }
        int[] buf = new int[m];
        // If self starts with a sign, preserve it at the front
        if (n >= 1 && ((c = value[0]) == '-' || c == '+')) {
            start = 1;
            buf[0] = c;
        }
        // The fill
        for (int i = start; i < fill + start; i++) { buf[i] = '0'; }
        // The original without its sign (if any)
        System.arraycopy(value, start, buf, fill + start, n - start);
        return new PyUnicode(TYPE, buf);
    }

    @PythonMethod(primary = false)
    static String zfill(String self, int width) {
        int n = self.codePointCount(0, self.length());
        int m = Math.max(width, n), start = 0, fill = m - n, c;
        if (fill <= 0) { return self; }
        StringBuilder buf = new StringBuilder(m);
        // If self starts with a sign, preserve it at the front
        if (n >= 1 && ((c = self.codePointAt(0)) == '-' || c == '+')) {
            start = 1;
            buf.appendCodePoint(c);
        }
        // The fill
        for (int i = 0; i < fill; i++) { buf.append('0'); }
        // The original without its sign (if any)
        buf.append(self.substring(start));
        return buf.toString();
    }

    // Java-only API --------------------------------------------------

    private static final int HIGH_SURROGATE_OFFSET =
            Character.MIN_HIGH_SURROGATE
                    - (Character.MIN_SUPPLEMENTARY_CODE_POINT >>> 10);

    /**
     * The hash of a {@link PyUnicode} is the same as that of a Java
     * {@code String} equal to it. This is so that a given Python
     * {@code str} may be found as a match in hashed data structures,
     * whichever representation is used for the key or query.
     */
    @Override
    public int hashCode() throws PyException {
        return PyDict.pythonHash(this);
    }

    /**
     * Compare for equality with another Python {@code str}, or a
     * {@link PyDict.Key} containing a {@code str}. If the other object
     * is not a {@code str}, or a {@code Key} containing a {@code str},
     * return {@code false}. If it is such an object, compare for
     * equality of the code points.
     */
    @Override
    public boolean equals(Object obj) {
        return PyDict.pythonEquals(this, obj);
    }

    /**
     * Create a {@code str} from a format and arguments. Note Java
     * {@code String.format} semantics are applied, not the CPython
     * ones.
     *
     * @param fmt format string (Java semantics)
     * @param args arguments
     * @return formatted string
     */
    @Deprecated // XXX possibly want a version with Python semantics
    static PyUnicode fromFormat(String fmt, Object... args) {
        return new PyUnicode(TYPE, String.format(fmt, args));
    }

    @Override
    public PyType getType() { return type; }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        for (int c : value) { b.appendCodePoint(c); }
        return b.toString();
    }

    /**
     * Present a Python {@code str} as a Java {@code String} value or
     * raise a {@link TypeError}. This is for use when the argument is
     * expected to be a Python {@code str} or a sub-class of it.
     *
     * @param v claimed {@code str}
     * @return {@code String} value
     * @throws TypeError if {@code v} is not a Python {@code str}
     */
    static String asString(Object v) throws TypeError {
        if (v instanceof String)
            return (String)v;
        else if (v instanceof PyUnicode)
            return ((PyUnicode)v).toString();
        throw Abstract.requiredTypeError("a str", v);
    }

    // Plumbing -------------------------------------------------------

    /**
     * Convert a Python {@code str} to a Java {@code str} (or throw
     * {@link NoConversion}).
     * <p>
     * If the method throws the special exception {@link NoConversion},
     * the caller must deal with it by throwing an appropriate Python
     * exception or taking an alternative course of action.
     *
     * @param v to convert
     * @return converted to {@code String}
     * @throws NoConversion v is not a {@code str}
     */
    static String convertToString(Object v) throws NoConversion {
        if (v instanceof String)
            return (String)v;
        else if (v instanceof PyUnicode)
            return ((PyUnicode)v).toString();
        throw PyObjectUtil.NO_CONVERSION;
    }

    /**
     * Wrap a Java {@code String} as a {@link PySequence.Delegate}, that
     * is also a an iterable of {@code int}. If the {@code String}
     * includes surrogate pairs of {@code char}s, these are interpreted
     * as a single Python code point.
     */
    static class StringAdapter
            extends PySequence.Delegate<Object, Object>
            implements PySequence.OfInt {

        /** Value of the str encoded as a Java {@code String}. */
        private final String s;

        /** Length in code points deduced from the {@code String}. */
        private final int len;

        /**
         * Adapt a String so we can iterate or stream its code points.
         *
         * @param s to adapt
         */
        StringAdapter(String s) {
            this.s = s;
            len = s.codePointCount(0, s.length());
        }

        /**
         * Return {@code true} iff the string contains only basic plane
         * characters or, possibly, isolated surrogates. All
         * {@code char}s may be treated as code points.
         *
         * @return contains only BMP characters or isolated surrogates
         */
        private boolean isBMP() { return len == s.length(); }

        @Override
        public int length() { return len; };

        @Override
        public PyType getType() { return TYPE; }

        @Override
        public String getTypeName() { return "string"; }

        @Override
        public Object getItem(int i) {
            return PyUnicode.fromCodePoint(get(i));
        }

        @Override
        public Integer get(int i) {
            if (isBMP()) {
                // Treating surrogates as characters
                return Integer.valueOf(s.charAt(i));
            } else {
                // We have to count from the start
                try {
                    Stream<Integer> cps = asStream().skip(i);
                    return cps.findFirst().orElseThrow();
                } catch (NoSuchElementException nse) {
                    // getItem should only be called with checked i
                    throw new InterpreterError(nse, "index=%d", i);
                }
            }
        }

        @Override
        public Object getSlice(Indices slice) throws Throwable {
            if (slice.step == 1 && isBMP()) {
                return s.substring(slice.start, slice.stop);
            } else {
                /*
                 * If the code points are not all BMP, it is less work
                 * in future if we use a PyUnicode. If step != 1, there
                 * is the possibility of creating an unintended
                 * surrogate pair, so only a PyUnicode should be trusted
                 * to represent the result.
                 */
                int L = slice.slicelength, i = slice.start;
                int[] r = new int[L];
                if (isBMP()) {
                    // Treating surrogates as characters
                    for (int j = 0; j < L; j++) {
                        r[j] = s.charAt(i);
                        i += slice.step;
                    }
                } else {
                    // We have to count from the start
                    Iterator<Integer> cps = iterator();
                    int j = 0;
                    while (j++ < slice.start) { cps.next(); }
                    for (j = 0; j < L; j++) {
                        r[j] = cps.next();
                        for (int k = 1; k < slice.step; k++) {
                            cps.next();
                        }
                    }
                }
                return new PyUnicode(TYPE, true, r);
            }
        }

        @Override
        Object add(Object ow)
                throws OutOfMemoryError, NoConversion, Throwable {
            if (ow instanceof String) {
                return PyUnicode.concat(s, (String)ow);
            } else {
                IntStream w = adapt(ow).asIntStream();
                return concatUnicode(s.codePoints(), w);
            }
        }

        @Override
        Object radd(Object ov)
                throws OutOfMemoryError, NoConversion, Throwable {
            if (ov instanceof String) {
                return PyUnicode.concat((String)ov, s);
            } else {
                IntStream v = adapt(ov).asIntStream();
                return concatUnicode(v, s.codePoints());
            }
        }

        @Override
        Object repeat(int n) throws OutOfMemoryError, Throwable {
            if (n == 0)
                return "";
            else if (n == 1 || len == 0)
                return s;
            else if (Character.isLowSurrogate(s.charAt(0))
                    && Character.isHighSurrogate(s.charAt(len - 1)))
                /*
                 * s ends with a high surrogate and starts with a low
                 * surrogate, so simply concatenated to itself by
                 * String.repeat, these would merge into one character.
                 * Only a PyUnicode properly represents the result.
                 */
                return (new PyUnicode(TYPE, s)).delegate.repeat(n);
            else
                // Java String repeat will do fine
                return s.repeat(n);
        }

        // Iterable<Object> interface ---------------------------------

        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<Integer>() {

                private int i = 0;

                @Override
                public boolean hasNext() { return i < s.length(); }

                @Override
                public Integer next() {
                    // Treating surrogate pairs as two characters
                    return get(i++);
                }
            };
        }

        // PySequence.Of<Object> interface ----------------------------

        @Override
        public Spliterator.OfInt spliterator() {
            return s.codePoints().spliterator();
        }

        @Override
        public IntStream asIntStream() { return s.codePoints(); }

        @Override
        public int compareTo(PySequence.Of<Integer> other) {
            int n = s.length();
            Iterator<Integer> ib = other.iterator();
            // Not treating surrogate pairs as one code point
            for (int i = 0; i < n; i++) {
                int a = s.charAt(i);
                if (ib.hasNext()) {
                    int b = ib.next();
                    // if a != b, then we've found an answer
                    if (a > b)
                        return 1;
                    else if (a < b)
                        return -1;
                } else
                    // s has not run out, but b has. s wins
                    return 1;
            }
            /*
             * The sequences matched over the length of s. The other is
             * the winner if it still has elements. Otherwise its a tie.
             */
            return ib.hasNext() ? -1 : 0;
        }
    }

    /**
     * A class to act as the delegate implementing {@code __getitem__}
     * and other index-related operations. By inheriting {@link Delegate
     * PySequence.Delegate} in this inner class, we obtain boilerplate
     * implementation code for slice translation and range checks. We
     * need only specify the work specific to {@link PyUnicode}
     * instances.
     */
    class UnicodeDelegate extends PySequence.Delegate<Object, Object>
            implements PySequence.OfInt {

        @Override
        public int length() { return value.length; }

        @Override
        public PyType getType() { return TYPE; }

        @Override
        public String getTypeName() { return "string"; }

        @Override
        public Object getItem(int i) {
            return PyUnicode.fromCodePoint(value[i]);
        }

        @Override
        public Object getSlice(Indices slice) {
            int[] v;
            if (slice.step == 1)
                v = Arrays.copyOfRange(value, slice.start, slice.stop);
            else {
                v = new int[slice.slicelength];
                int i = slice.start;
                for (int j = 0; j < slice.slicelength; j++) {
                    v[j] = value[i];
                    i += slice.step;
                }
            }
            return new PyUnicode(TYPE, true, v);
        }

        @Override
        Object add(Object ow)
                throws OutOfMemoryError, NoConversion, Throwable {
            if (ow instanceof PyUnicode) {
                // Optimisation (or is it?) over concatUnicode
                PyUnicode w = (PyUnicode)ow;
                int L = value.length, M = w.value.length;
                int[] r = new int[L + M];
                System.arraycopy(value, 0, r, 0, L);
                System.arraycopy(w.value, 0, r, L, M);
                return new PyUnicode(TYPE, true, r);
            } else {
                return concatUnicode(asIntStream(),
                        adapt(ow).asIntStream());
            }
        }

        @Override
        Object radd(Object ov)
                throws OutOfMemoryError, NoConversion, Throwable {
            if (ov instanceof PyUnicode) {
                // Optimisation (or is it?) over concatUnicode
                PyUnicode v = (PyUnicode)ov;
                int L = v.value.length, M = value.length;
                int[] r = new int[L + M];
                System.arraycopy(v.value, 0, r, 0, L);
                System.arraycopy(value, 0, r, L, M);
                return new PyUnicode(TYPE, true, r);
            } else {
                return concatUnicode(adapt(ov).asIntStream(),
                        asIntStream());
            }
        }

        @Override
        Object repeat(int n) throws OutOfMemoryError, Throwable {
            int m = value.length;
            if (n == 0)
                return "";
            else if (n == 1 || m == 0)
                return PyUnicode.this;
            else {
                int[] b = new int[n * m];
                for (int i = 0, p = 0; i < n; i++, p += m) {
                    System.arraycopy(value, 0, b, p, m);
                }
                return new PyUnicode(TYPE, true, b);
            }
        }

        // PySequence.OfInt interface ---------------------------------

        @Override
        public Integer get(int i) { return Integer.valueOf(value[i]); }

        @Override
        public Spliterator.OfInt spliterator() {
            final int flags = Spliterator.IMMUTABLE | Spliterator.SIZED
                    | Spliterator.ORDERED;
            return Spliterators.spliterator(value, flags);
        }

        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<Integer>() {

                private int i = 0;

                @Override
                public boolean hasNext() { return i < value.length; }

                @Override
                public Integer next() { return value[i++]; }
            };
        }

        @Override
        public IntStream asIntStream() {
            int flags = Spliterator.IMMUTABLE | Spliterator.SIZED;
            Spliterator.OfInt s =
                    Spliterators.spliterator(value, flags);
            return StreamSupport.intStream(s, false);
        }

        @Override
        public int compareTo(PySequence.Of<Integer> other) {
            Iterator<Integer> ib = other.iterator();
            for (int a : value) {
                if (ib.hasNext()) {
                    int b = ib.next();
                    // if a != b, then we've found an answer
                    if (a > b)
                        return 1;
                    else if (a < b)
                        return -1;
                } else
                    // value has not run out, but other has. We win.
                    return 1;
            }
            /*
             * The sequences matched over the length of value. The other
             * is the winner if it still has elements. Otherwise its a
             * tie.
             */
            return ib.hasNext() ? -1 : 0;
        }

        /**
         * Compare for equality with a sequence. This is a little
         * simpler than {@code compareTo}.
         *
         * @param b another
         * @return whether values equal
         */
        boolean equals(PySequence.Of<Integer> b) {
            // XXX Do we use this?
            // Lengths must be equal
            if (length() != b.length()) { return false; }
            // Scan the codes points in this.value and b
            Iterator<Integer> ib = b.iterator();
            for (int c : value) {
                if (c != ib.next()) { return false; }
            }
            return true;
        }
    }

    /**
     * Adapt a Python {@code str} to a sequence of Java {@code Integer}
     * values or raise a {@link TypeError}. This is for use when the
     * argument is expected to be a Python {@code str} or a sub-class of
     * it.
     *
     * @param v claimed {@code str}
     * @return {@code int} value
     * @throws TypeError if {@code v} is not a Python {@code str}
     */
    static PySequence.Of<Integer> asSeq(Object v) throws TypeError {
        // Check against supported types, most likely first
        if (v instanceof String)
            return new StringAdapter((String)v);
        else if (v instanceof PyUnicode)
            return ((PyUnicode)v).delegate;
        throw Abstract.requiredTypeError("a str", v);
    }

    /**
     * Adapt a Python {@code str} to a sequence of Java {@code int}
     * values or throw an exception. If the method throws the special
     * exception {@link NoConversion}, the caller must catch it and deal
     * with it, perhaps by throwing a {@link TypeError}. A binary
     * operation will normally return {@link Py#NotImplemented} in that
     * case.
     * <p>
     * Note that implementing {@link PySequence.OfInt} is not enough,
     * which other types may, but be incompatible in Python.
     *
     * @param v to wrap or return
     * @return adapted to a sequence
     * @throws NoConversion if {@code v} is not a Python {@code str}
     */
    static PySequence.OfInt adapt(Object v) throws NoConversion {
        // Check against supported types, most likely first
        if (v instanceof String)
            return new StringAdapter((String)v);
        else if (v instanceof PyUnicode)
            return ((PyUnicode)v).delegate;
        throw PyObjectUtil.NO_CONVERSION;
    }

    /**
     * Short-cut {@link #adapt(Object)} when type statically known.
     *
     * @param v to wrap
     * @return new StringAdapter(v)
     */
    static StringAdapter adapt(String v) {
        return new StringAdapter(v);
    }

    /**
     * Short-cut {@link #adapt(Object)} when type statically known.
     *
     * @return the delegate for sequence operations on this {@code str}
     */
    UnicodeDelegate adapt() { return delegate; }

    /**
     * Concatenate two {@code String} representations of {@code str}
     * into a {@code PyUnicode}. This method almost always calls
     * {@code String.concat(v, w)} and almost always returns a
     * {@code String}. There is a delicate case where {@code v} ends
     * with a high surrogate and {@code w} starts with a low surrogate.
     * Simply concatenated, these merge into one character. Only a
     * PyUnicode properly represents the result in that case.
     *
     * @param v first string to concatenate
     * @param w second string to concatenate
     * @return the concatenation {@code v + w}
     */
    private static Object concat(String v, String w)
            throws OutOfMemoryError {
        /*
         * Since we have to guard against empty strings, we may as well
         * take the optimisation these paths invite.
         */
        int vlen = v.length();
        if (vlen == 0)
            return w;
        else if (w.length() == 0)
            return v;
        else if (Character.isLowSurrogate(w.charAt(0))
                && Character.isHighSurrogate(v.charAt(vlen - 1)))
            // Only a PyUnicode properly represents the result
            return concatUnicode(v.codePoints(), w.codePoints());
        else {
            // Java String concatenation will do fine
            return v.concat(w);
        }
    }

    /**
     * Concatenate two streams of code points into a {@code PyUnicode}.
     *
     * @param v first string to concatenate
     * @param w second string to concatenate
     * @return the concatenation {@code v + w}
     * @throws OutOfMemoryError when the concatenated string is too long
     */
    private static PyUnicode concatUnicode(IntStream v, IntStream w)
            throws OutOfMemoryError {
        return new PyUnicode(TYPE, true,
                IntStream.concat(v, w).toArray());
    }

    // Python sub-class -----------------------------------------------

    /**
     * Instances in Python of sub-classes of 'int', are represented in
     * Java by instances of this class.
     */
    static class Derived extends PyUnicode implements DictPyObject {

        protected Derived(PyType subType, String value) {
            super(subType, value);
        }

        protected Derived(PyType subType, int[] value) {
            super(subType, value);
        }

        /** The instance dictionary {@code __dict__}. */
        protected PyDict dict = new PyDict();

        @Override
        public Map<Object, Object> getDict() { return dict; }
    }
}
