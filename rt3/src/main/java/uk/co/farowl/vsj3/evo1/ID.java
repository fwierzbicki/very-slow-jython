package uk.co.farowl.vsj3.evo1;

import java.util.HashMap;
import java.util.Map;

// XXX Possibly don't need this at all (or it is just String.intern).
// Keeping for now to satisfy references
/**
 * Constants for names used frequently in the runtime. These may be
 * supplied as keys in a dictionary look-up. Every constant is textually
 * equal to its own name.
 */
// This file could easily be generated by a script (but wasn't).
class ID {

    private static Map<String, PyUnicode> ids = new HashMap<>();

    static final PyUnicode __isabstractmethod__ =
            id("__isabstractmethod__");
    static final PyUnicode __add__ = id("__add__");
    static final PyUnicode __and__ = id("__and__");
    static final PyUnicode __bases__ = id("__bases__");
    static final PyUnicode __build_class__ = id("__build_class__");
    static final PyUnicode __builtins__ = id("__builtins__");
    static final PyUnicode __class__ = id("__class__");
    static final PyUnicode __delete__ = id("__delete__");
    static final PyUnicode __doc__ = id("__doc__");
    static final PyUnicode __eq__ = id("__eq__");
    static final PyUnicode __float__ = id("__float__");
    static final PyUnicode __ge__ = id("__ge__");
    static final PyUnicode __get__ = id("__get__");
    static final PyUnicode __gt__ = id("__gt__");
    static final PyUnicode __hash__ = id("__hash__");
    static final PyUnicode __instancecheck__ = id("__instancecheck__");
    static final PyUnicode __le__ = id("__le__");
    static final PyUnicode __len__ = id("__len__");
    static final PyUnicode __lt__ = id("__lt__");
    static final PyUnicode __mro_entries__ = id("__mro_entries__");
    static final PyUnicode __mul__ = id("__mul__");
    static final PyUnicode __name__ = id("__name__");
    static final PyUnicode __ne__ = id("__ne__");
    static final PyUnicode __neg__ = id("__neg__");
    static final PyUnicode __new__ = id("__new__");
    static final PyUnicode __qualname__ = id("__qualname__");
    static final PyUnicode __repr__ = id("__repr__");
    static final PyUnicode __radd__ = id("__radd__");
    static final PyUnicode __rand__ = id("__rand__");
    static final PyUnicode __rmul__ = id("__rmul__");
    static final PyUnicode __rsub__ = id("__rsub__");
    static final PyUnicode __self__ = id("__self__");
    static final PyUnicode __set__ = id("__set__");
    static final PyUnicode __sub__ = id("__sub__");
    static final PyUnicode __str__ = id("__str__");
    static final PyUnicode __subclasscheck__ = id("__subclasscheck__");

    static final PyUnicode False = id("False");
    static final PyUnicode None = id("None");
    static final PyUnicode NotImplemented = id("NotImplemented");
    static final PyUnicode True = id("True");

    static final PyUnicode bool = id("bool");
    static final PyUnicode bytes = id("bytes");
    static final PyUnicode copy = id("copy");
    static final PyUnicode dict = id("dict");
    static final PyUnicode get = id("get");
    static final PyUnicode getattr = id("getattr");
    static final PyUnicode items = id("items");
    static final PyUnicode keys = id("keys");
    static final PyUnicode list = id("list");
    static final PyUnicode object = id("object");
    static final PyUnicode str = id("str");
    static final PyUnicode tuple = id("tuple");
    static final PyUnicode type = id("type");
    static final PyUnicode values = id("values");

    /**
     * Find or create, and intern, a Python <code>str</code> for the
     * Java String. If one is defined, it is more efficient to use the
     * static member of the same name.
     *
     * @param name to intern (or look up)
     * @return the Python {@code str} with that value
     */
    static PyUnicode intern(String name) {
        PyUnicode u = ids.get(name);
        if (u == null)
            ids.put(name, u = new PyUnicode(name));
        return u;
    }

    /**
     * Intern a new Python <code>str</code> for the Java String. If one
     * is defined already, it is an error.
     *
     * @param name to intern
     * @return the Python {@code str} with that value
     */
    private static PyUnicode id(String name) {
        PyUnicode u = new PyUnicode(name);
        PyUnicode previous = ids.put(name, u);
        if (previous != null) {
            throw new InterpreterError("repeat definition of ID.%s",
                    name);
        }
        return u;
    }

    private ID() {} // No instances
}
