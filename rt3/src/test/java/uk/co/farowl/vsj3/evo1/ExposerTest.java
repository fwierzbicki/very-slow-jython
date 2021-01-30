package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.Map;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj3.evo1.Exposed.DocString;
import uk.co.farowl.vsj3.evo1.Exposed.JavaMethod;
import uk.co.farowl.vsj3.evo1.Exposed.Member;
import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;

/**
 * Unit tests for the {@link Exposer} and the {@link Descriptor}s it
 * produces for the several kinds of annotation that may be applied to
 * Java classes implementing Python types.
 */

class ExposerTest {

//    /**
//     * A test class with exposed members. This doesn't have to be a
//     * Python object, but formally implements {@link CraftedType} in
//     * order to be an acceptable target for get and set operations.
//     */
//    private static class ObjectWithMembers implements CraftedType {
//
//        /** Lookup object to support creation of descriptors. */
//        private static final Lookup LOOKUP = MethodHandles.lookup();
//
//        static PyType TYPE =
//                PyType.fromSpec(new PyType.Spec("ObjectWithMembers",
//                        ObjectWithMembers.class, LOOKUP));
//
//        @Override
//        public PyType getType() { return TYPE; }
//
//        @Member
//        int i;
//        @Member
//        @DocString("My test x")
//        double x;
//        /** String with change of name. */
//        @Member("text")
//        String t;
//        /** String can be properly deleted without popping up as None */
//        @Member(optional = true)
//        String s;
//        /** {@code Object} member */
//        @Member
//        Object obj;
//        /** {@code PyUnicode} member: care needed on set. */
//        @Member
//        PyUnicode strhex;
//
//        /** Read-only access. */
//        @Member(readonly = true)
//        int i2;
//        /** Read-only access since final. */
//        @Member
//        final double x2;
//        /** Read-only access given first. */
//        @Member(readonly = true, value = "text2")
//        String t2;
//
//        ObjectWithMembers(double value) {
//            x2 = x = value;
//            i2 = i = Math.round((float) value);
//            t2 = t = s = String.format("%d", i);
//            obj = new PyUnicode(Integer.toString(i));
//            strhex = new PyUnicode(Integer.toString(i, 16));
//        }
//    }
//
//    /**
//     * A class that extends the above, with the same Python type. We
//     * want to check that what we're doing to reflect on the parent
//     * produces descriptors we can apply to a sub-class.
//     */
//    private static class DerivedWithMembers extends ObjectWithMembers {
//
//        DerivedWithMembers(double value) {
//            super(value);
//        }
//    }
//
//    /**
//     * Test that the {@link Exposer} creates {@link PyMemberDescr}s for
//     * fields annotated as {@link Member}.
//     */
//    // @Test
//    void memberConstruct() {
//        // Repeat roughly what PyType.fromSpec already did.
//        Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
//                ObjectWithMembers.LOOKUP, ObjectWithMembers.class,
//                ObjectWithMembers.TYPE);
//        // Try a few attributes of i
//        assertTrue(mds.containsKey("i"));
//        PyMemberDescr md = mds.get("i");
//        assertEquals("<member 'i' of 'ObjectWithMembers' objects>",
//                md.toString());
//        assertNull(md.doc);
//        assertTrue(md.flags.isEmpty());
//        // Try a few attributes of x
//        md = mds.get("x");
//        assertEquals("My test x", md.doc);
//        assertTrue(md.flags.isEmpty());
//        // Now text2
//        md = mds.get("text2");
//        assertNull(md.doc);
//        assertEquals(EnumSet.of(PyMemberDescr.Flag.READONLY), md.flags);
//    }
//
//    /**
//     * Test that we can get values via the {@link PyMemberDescr}s the
//     * {@link Exposer} creates for fields annotated as {@link Member}.
//     */
//    // @Test
//    void memberGetValues() {
//        Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
//                ObjectWithMembers.LOOKUP, ObjectWithMembers.class,
//                ObjectWithMembers.TYPE);
//        ObjectWithMembers o = new ObjectWithMembers(42.0);
//        ObjectWithMembers p = new ObjectWithMembers(-1.0);
//
//        // Same PyMemberDescr, different objects
//        PyMemberDescr md_i = mds.get("i");
//        assertEquals(Py.val(42), md_i.__get__(o, null));
//        assertEquals(Py.val(-1), md_i.__get__(p, null));
//
//        PyMemberDescr md_x = mds.get("x");
//        assertEquals(Py.val(42.0), md_x.__get__(o, null));
//
//        PyMemberDescr md_t = mds.get("text");
//        assertEquals(Py.str("-1"), md_t.__get__(p, null));
//
//        PyMemberDescr md_s = mds.get("s");
//        assertEquals(Py.str("42"), md_s.__get__(o, null));
//
//        PyMemberDescr md_obj = mds.get("obj");  // Object
//        assertEquals(Py.str("42"), md_obj.__get__(o, null));
//
//        PyMemberDescr md_strhex = mds.get("strhex");  // Object
//        assertEquals(Py.str("2a"), md_strhex.__get__(o, null));
//
//        // Read-only cases work too
//        PyMemberDescr md_i2 = mds.get("i2");
//        assertEquals(Py.val(42), md_i2.__get__(o, null));
//        assertEquals(Py.val(-1), md_i2.__get__(p, null));
//
//        PyMemberDescr md_x2 = mds.get("x2");
//        assertEquals(Py.val(42.0), md_x2.__get__(o, null));
//
//        PyMemberDescr md_t2 = mds.get("text2");
//        assertEquals(Py.str("-1"), md_t2.__get__(p, null));
//    }
//
//    /**
//     * Test that we can set values via the {@link PyMemberDescr}s the
//     * {@link Exposer} creates for fields annotated as {@link Member},
//     * and receive an {@link AttributeError} when read-only.
//     *
//     * @throws Throwable unexpectedly
//     * @throws TypeError unexpectedly
//     */
//    // @Test
//    void memberSetValues() throws TypeError, Throwable {
//        Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
//                ObjectWithMembers.LOOKUP, ObjectWithMembers.class,
//                ObjectWithMembers.TYPE);
//        final ObjectWithMembers o = new ObjectWithMembers(42.0);
//        final ObjectWithMembers p = new ObjectWithMembers(-1.0);
//
//        int i = 43, j = 44;
//        final Object oi = Py.val(i);
//        final Object oj = Py.val(j);
//        double x = 9.0;
//        final Object ox = Py.val(x);
//        String t = "Gumby";
//        final Object ot = Py.str(t);
//
//        // Same descriptor applicable to different objects
//        PyMemberDescr md_i = mds.get("i");
//        md_i.__set__(o, oi);
//        md_i.__set__(p, oj);
//        assertEquals(i, o.i);
//        assertEquals(j, p.i);
//
//        // Set a double
//        PyMemberDescr md_x = mds.get("x");
//        md_x.__set__(o, ox);
//        assertEquals(x, o.x, 1e-6);
//
//        // Set a String
//        PyMemberDescr md_t = mds.get("text");
//        md_t.__set__(p, ot);
//        assertEquals(t, p.t);
//
//        PyMemberDescr md_obj = mds.get("obj");  // Object
//        md_obj.__set__(p, ox);
//        assertSame(ox, p.obj);
//
//        PyMemberDescr md_strhex = mds.get("strhex");  // PyUnicode
//        md_strhex.__set__(p, ot);
//        assertSame(ot, p.strhex);
//
//        // It is a TypeError to set the wrong kind of value
//        assertThrows(TypeError.class, () -> md_i.__set__(o, ot));
//        assertThrows(TypeError.class, () -> md_t.__set__(o, oi));
//        assertThrows(TypeError.class, () -> md_strhex.__set__(o, ox));
//
//        // It is an AttributeError to set a read-only attribute
//        final PyMemberDescr md_i2 = mds.get("i2");
//        assertThrows(AttributeError.class, () -> md_i2.__set__(o, oi));
//        assertThrows(AttributeError.class, () -> md_i2.__set__(p, oj));
//
//        final PyMemberDescr md_x2 = mds.get("x2");
//        assertThrows(AttributeError.class, () -> md_x2.__set__(o, ox));
//
//        final PyMemberDescr md_text2 = mds.get("text2");
//        assertThrows(AttributeError.class,
//                () -> md_text2.__set__(p, ot));
//    }
//
//    /**
//     * Test that we can delete values via the {@link PyMemberDescr}s the
//     * {@link Exposer} creates for fields annotated as {@link Member},
//     * and receive an {@link AttributeError} when read-only.
//     *
//     * @throws Throwable unexpectedly
//     * @throws TypeError unexpectedly
//     */
//    // @Test
//    void memberDeleteValues() throws TypeError, Throwable {
//        Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
//                ObjectWithMembers.LOOKUP, ObjectWithMembers.class,
//                ObjectWithMembers.TYPE);
//        final ObjectWithMembers o = new ObjectWithMembers(42.0);
//        final PyType T = ObjectWithMembers.TYPE;
//
//        String s = "P.J.", t = "Gumby";
//        final Object os = Py.str(s);
//        final Object ot = Py.str(t);
//
//        /*
//         * The text (o.t) attribute is a writable, non-optional String
//         */
//        PyMemberDescr md_t = mds.get("text");
//        assertEquals("42", o.t);
//        // Deleting it makes it null internally, None externally
//        md_t.__delete__(o);
//        assertEquals(null, o.t);
//        assertEquals(Py.None, md_t.__get__(o, T));
//        // We can delete it again (no error) and set it
//        md_t.__delete__(o);
//        md_t.__set__(o, ot);
//        assertEquals(t, o.t);
//
//        /*
//         * The s attribute is a writable, optional String
//         */
//        PyMemberDescr md_s = mds.get("s");
//        md_s.__set__(o, os);
//        assertEquals(s, o.s);
//        assertEquals(os, md_s.__get__(o, T));
//        // Deleting it makes it null internally, vanish externally
//        md_s.__delete__(o);
//        assertEquals(null, o.s);
//        assertThrows(AttributeError.class, () -> md_s.__get__(o, T));
//        // Deleting it again is an error
//        assertThrows(AttributeError.class, () -> md_s.__delete__(o));
//        // But we can set it
//        md_s.__set__(o, ot);
//        assertEquals(t, o.s);
//
//        /*
//         * i, x are primitives, so cannot be deleted.
//         */
//        // Deleting a primitive is a TypeError
//        PyMemberDescr md_i = mds.get("i");
//        assertThrows(TypeError.class, () -> md_i.__delete__(o));
//        final PyMemberDescr md_x = mds.get("x");
//        assertThrows(TypeError.class, () -> md_x.__delete__(o));
//
//        /*
//         * i2, x2, text2 (o.t) are read-only, so cannot be deleted.
//         */
//        // Deleting a read-only is an AttributeError
//        final PyMemberDescr md_i2 = mds.get("i2");
//        assertThrows(AttributeError.class, () -> md_i2.__delete__(o));
//        final PyMemberDescr md_x2 = mds.get("x2");
//        assertThrows(AttributeError.class, () -> md_x2.__delete__(o));
//        final PyMemberDescr md_text2 = mds.get("text2");
//        assertThrows(AttributeError.class,
//                () -> md_text2.__delete__(o));
//    }
//
//    /**
//     * Test that we can get and set values in a Java sub-class via the
//     * {@link MemberDef}s the {@link Exposer} creates.
//     *
//     * @throws Throwable unexpectedly
//     * @throws TypeError unexpectedly
//     */
//    // @Test
//    void memberInDerived() throws TypeError, Throwable {
//        // Note we make the table for the super-class
//        Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
//                ObjectWithMembers.LOOKUP, ObjectWithMembers.class,
//                ObjectWithMembers.TYPE);
//        // But the test object is the sub-class
//        final DerivedWithMembers o = new DerivedWithMembers(42.0);
//
//        int i = 45;
//        final Object oi = Py.val(i);
//        double x = 9.0;
//        final Object ox = Py.val(x);
//        String t = "Gumby";
//        final Object ot = Py.str(t);
//
//        // Set then get
//        PyMemberDescr md_i = mds.get("i");
//        md_i.__set__(o, oi);
//        assertEquals(oi, md_i.__get__(o, null));
//
//        PyMemberDescr md_x = mds.get("x");
//        md_x.__set__(o, ox);
//        assertEquals(x, o.x, 1e-6);
//
//        PyMemberDescr md_t = mds.get("text");
//        md_t.__set__(o, ot);
//        assertEquals(t, o.t);
//        assertEquals(ot, md_t.__get__(o, null));
//
//        // Read-only cases throw
//        final PyMemberDescr md_i2 = mds.get("i2");
//        assertThrows(AttributeError.class, () -> md_i2.__set__(o, oi));
//    }

    /**
     * Model canonical implementation to explore exposure of a special
     * method.
     */
    private static class PyObjectWithSpecial implements CraftedType {

        /** Lookup object to support creation of descriptors. */
        private static final Lookup LOOKUP = MethodHandles.lookup();
        static PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("ObjectWithSpecialMethods",
                        PyObjectWithSpecial.class, LOOKUP)
                                .accept(AcceptedSpecial.class));
        int value;

        @SuppressWarnings("unused")
        public PyObjectWithSpecial(int value) {
            this.value = value;
        }

        @SuppressWarnings("unused")
        Object __neg__() {
            return new PyObjectWithSpecial(-value);
        }

        @SuppressWarnings("unused")
        static Object __neg__(AcceptedSpecial v) {
            return new AcceptedSpecial(-v.value);
        }

        @Override
        public PyType getType() { return TYPE; }
    }

    /**
     * Model accepted implementation to explore exposure of a special
     * method.
     */
    private static class AcceptedSpecial {
        int value;
        public AcceptedSpecial(int value) {
            this.value = value;
        }
    }

    /**
     * Test that we get working descriptors of type
     * {@link PyWrapperDescr}s the {@link Exposer} creates for methods
     * with special names.
     *
     * @throws Throwable unexpectedly
     * @throws AttributeError unexpectedly
     */
    // @Test
    void wrapperConstruct() throws AttributeError, Throwable {
        // Roughly what PyType.fromSpec does in real life.
        Map<String, PyWrapperDescr> wds = Exposer.wrapperDescrs(
                PyObjectWithSpecial.LOOKUP, PyObjectWithSpecial.class,
                PyObjectWithSpecial.TYPE);

        // We defined this special method
        PyWrapperDescr neg = wds.get("__neg__");

        assertEquals("__neg__", neg.name);
        assertEquals(PyObjectWithSpecial.TYPE, neg.objclass);
        assertEquals(
                "<slot wrapper '__neg__' of 'ObjectWithSpecialMethods' objects>",
                neg.toString());
    }

//    private static class PyObjectWithMethods implements CraftedType {
//
//        /** Lookup object to support creation of descriptors. */
//        private static final Lookup LOOKUP = MethodHandles.lookup();
//        static PyType TYPE = PyType.fromSpec( //
//                new PyType.Spec("PyObjectWithMethods",
//                        PyObjectWithMethods.class, LOOKUP));
//        String value;
//
//        public PyObjectWithMethods(String value) {
//            this.value = value;
//        }
//
//        // Methods using Java primitives -----------------------------
//
//        @JavaMethod
//        int length() {
//            return value.length();
//        }
//
//        @JavaMethod
//        double density(String ch) {
//            int n = value.length(), count = 0;
//            if (ch.length() != 1) {
//                throw new TypeError("arg must be single character");
//            } else if (n > 0) {
//                char c = ch.charAt(0);
//                for (int i = 0; i < n; i++) {
//                    if (value.charAt(i) == c) { count++; }
//                }
//                return ((double) count) / n;
//            } else {
//                return 0.0;
//            }
//        }
//
//        // Methods using Python only types ---------------------------
//
//        @PythonMethod
//        Object upper() {
//            return Py.str(value.toUpperCase());
//        }
//
//        @PythonMethod
//        Object find(PyTuple args) {
//            // No intention of processing arguments robustly
//            Object target = args.get(0);
//            if (target instanceof PyUnicode) {
//                int n = value.indexOf(((PyUnicode) target).value);
//                return Py.val(n);
//            } else {
//                throw new TypeError("target must be string");
//            }
//        }
//
//        @PythonMethod
//        Object encode(PyTuple args, PyDict kwargs) {
//            // No intention of processing arguments robustly
//            Object encoding = kwargs.get("encoding");
//            if (encoding instanceof PyUnicode) {
//                Charset cs =
//                        Charset.forName(((PyUnicode) encoding).value);
//                ByteBuffer bb = cs.encode(value);
//                byte[] b = new byte[bb.limit()];
//                bb.get(b);
//                return null; // return new PyBytes(b);
//            } else {
//                throw new TypeError("encoding must be string");
//            }
//        }
//
//        @Override
//        public PyType getType() { return TYPE; }
//    }
//
//    /**
//     * Test that we get working descriptors of type
//     * {@link PyMethodDescr}s from the {@link Exposer} for methods
//     * annotated in the test class {@link PyObjectWithMethods}.
//     *
//     * @throws Throwable unexpectedly
//     * @throws AttributeError unexpectedly
//     */
//    // @Test
//    void methodConstruct() throws AttributeError, Throwable {
//        // Roughly what PyType.fromSpec does in real life.
//        Map<String, PyMethodDescr> mds = Exposer.methodDescrs(
//                PyObjectWithMethods.LOOKUP, PyObjectWithMethods.class,
//                PyObjectWithMethods.TYPE);
//
//        // We defined this Java method
//        PyMethodDescr length = mds.get("length");
//
//        assertNotNull(length);
//        assertEquals("length", length.name);
//        assertEquals(PyObjectWithMethods.TYPE, length.objclass);
//        assertEquals(
//                "<method 'length' of 'PyObjectWithMethods' objects>",
//                length.toString());
//    }
//
//    /**
//     * Test that we can call {@link PyMethodDescr}s directly for methods
//     * annotated in the test class {@link PyObjectWithMethods}.
//     *
//     * @throws Throwable unexpectedly
//     */
//    // @Test
//    void methodDescrCall() throws AttributeError, Throwable {
//
//        PyType A = PyObjectWithMethods.TYPE;
//        String hello = "Hello World!";
//        Object a = new PyObjectWithMethods(hello);
//        Object result;
//
//        // length = A.length
//        PyMethodDescr length =
//                (PyMethodDescr) Abstract.getAttr(A, Py.str("length"));
//        assertEquals("length", length.name);
//        assertEquals(A, length.objclass);
//        // n = length(a) # = 12
//        PyTuple args = Py.tuple(a);
//        result = Callables.call(length, args, null);
//        assertEquals(hello.length(), Number.index(result).asSize());
//
//        // density = A.density(a, "l") # = 0.25
//        PyMethodDescr density =
//                (PyMethodDescr) Abstract.getAttr(A, Py.str("density"));
//        // Make a vector call
//        result = density.call(a, Py.str("l"));
//        assertEquals(0.25, Number.toFloat(result).doubleValue(), 1e-6);
//    }
//
//    /**
//     * Test that attribute access on {@link PyMethodDescr}s from the
//     * {@link Exposer} create bound method objects of type
//     * {@link PyJavaMethod}, for methods annotated in the test class
//     * {@link PyObjectWithMethods}.
//     *
//     * @throws Throwable unexpectedly
//     */
//    // @Test
//    void boundMethodConstruct() throws AttributeError, Throwable {
//        // Roughly what PyType.fromSpec does in real life.
//        Map<String, PyMethodDescr> mds = Exposer.methodDescrs(
//                PyObjectWithMethods.LOOKUP, PyObjectWithMethods.class,
//                PyObjectWithMethods.TYPE);
//
//        // Create an object of the right type
//        String hello = "Hello World!";
//        PyObjectWithMethods a = new PyObjectWithMethods(hello);
//
//        // We defined this Java method
//        PyMethodDescr length = mds.get("length");
//        PyJavaMethod bm = (PyJavaMethod) length.__get__(a, null);
//
//        assertNotNull(bm);
//        assertEquals(a, bm.self);
//        assertEquals(length.methodDef, bm.methodDef);
//        assertStartsWith(
//                "<built-in method length of PyObjectWithMethods object",
//                bm);
//    }
//
//    /**
//     * Test that we can call {@link PyJavaMethod}s created by attribute
//     * access on methods annotated in the test class
//     * {@link PyObjectWithMethods}.
//     *
//     * @throws Throwable unexpectedly
//     */
//    // @Test
//    void boundMethodCall() throws AttributeError, Throwable {
//
//        String hello = "Hello World!";
//        Object a = new PyObjectWithMethods(hello);
//        Object result;
//
//        // bm = a.length
//        PyJavaMethod bm =
//                (PyJavaMethod) Abstract.getAttr(a, Py.str("length"));
//        assertNotNull(bm);
//        assertEquals(a, bm.self);
//
//        // n = bm() # = 12
//        result = Callables.call(bm);
//        assertEquals(hello.length(), Number.index(result).asSize());
//
//        // m = a.density
//        bm = (PyJavaMethod) Abstract.getAttr(a, Py.str("density"));
//
//        // Force a classic call
//        // result = bm("l") # = 0.25
//        PyTuple args = Py.tuple(Py.str("l"));
//        result = bm.__call__(args, null);
//        assertEquals(0.25, Number.toFloat(result).doubleValue(), 1e-6);
//
//        // Make a vector call
//        // result = bm("l") # = 0.25
//        Object[] stack = new Object[] {Py.str("l")};
//        result = bm.call(stack, 0, 1, null);
//        assertEquals(0.25, Number.toFloat(result).doubleValue(), 1e-6);
//    }

    // Support methods -----------------------------------------------

    /** Assertion for prefix of a result. */
    private static void assertStartsWith(String expected,
            Object actual) {
        assertNotNull(actual);
        String actualString = actual.toString();
        int len = Math.min(expected.length(), actualString.length());
        String actualPrefix = actualString.substring(0, len);
        assertEquals(expected, actualPrefix);
    }

}
