package uk.co.farowl.vsj2.evo4;

import java.util.HashMap;
import java.util.Map;

/**
 * Constants for names used frequently in the runtime. These may be
 * supplied as keys in a dictionary look-up. Every constant is textually
 * equal to its own name.
 */
// This file could easily be generated by a script (but wasn't).
class ID {

    private static Map<String, PyUnicode> ids = new HashMap<>();

    static final PyUnicode __build_class__ = intern("__build_class__");
    static final PyUnicode __builtins__ = intern("__builtins__");
    static final PyUnicode __mro_entries__ = intern("__mro_entries__");
    static final PyUnicode __name__ = intern("__name__");

    static final PyUnicode None = intern("None");
    static final PyUnicode NotImplemented = intern("NotImplemented");
    static final PyUnicode False = intern("False");
    static final PyUnicode True = intern("True");
    static final PyUnicode bool = intern("bool");
    static final PyUnicode bytes = intern("bytes");
    static final PyUnicode dict = intern("dict");
    static final PyUnicode list = intern("list");
    static final PyUnicode object = intern("object");
    static final PyUnicode str = intern("str");
    static final PyUnicode tuple = intern("tuple");
    static final PyUnicode type = intern("type");

    /**
     * Find or create, and intern, a Python <code>str</code> for the
     * Java String. If one is defined, it is more efficient to use the
     * static member of the same name.
     *
     * @param name to intern (or look up)
     * @return the Python {@code str} withthat value
     */
    static PyUnicode intern(String name) {
        PyUnicode u = ids.get(name);
        if (u == null)
            ids.put(name, u = Py.str(name));
        return u;
    }
}