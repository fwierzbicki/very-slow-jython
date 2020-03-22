package uk.co.farowl.vsj2.evo3;

import java.util.ArrayList;

/** The Python {@code list} object. */
class PyList extends ArrayList<PyObject> implements PyObject {

    static final PyType TYPE = new PyType("list", PyList.class);

    @Override
    public PyType getType() { return TYPE; }

    /** Construct empty. */
    PyList() { super(); }

    /** Construct empty, with specified capacity. */
    PyList(int capacity) {
        super(capacity);
    }

    /** Construct from an array slice. */
    PyList(PyObject a[], int start, int count) {
        super(count);
        for (int i = start; i < start + count; i++) { add(a[i]); }
    }

    // slot functions -------------------------------------------------

    static PyObject sq_repeat(PyObject s, int n) {
        try {
            PyList self = (PyList) s;
            PyList r = new PyList(n * self.size());
            for (int i = 0; i < n; i++)
                r.addAll(self);
            return r;
        } catch (ClassCastException e) {
            throw PyObjectUtil.typeMismatch(s, TYPE);
        }
    }

    static int length(PyList s) { return s.size(); }

    static PyObject sq_item(PyObject s, int i) {
        try {
            return ((PyList) s).get(i);
        } catch (IndexOutOfBoundsException e) {
            throw new IndexError("list index out of range");
        } catch (ClassCastException e) {
            throw PyObjectUtil.typeMismatch(s, TYPE);
        }
    }

    static void sq_ass_item(PyObject s, int i, PyObject o) {
        try {
            ((PyList) s).set(i, o);
        } catch (IndexOutOfBoundsException e) {
            throw new IndexError("list index out of range");
        } catch (ClassCastException e) {
            throw PyObjectUtil.typeMismatch(s, TYPE);
        }
    }

    static PyObject mp_subscript(PyObject s, PyObject item)
            throws Throwable {
        try {
            PyList self = (PyList) s;
            PyType itemType = item.getType();
            if (Slot.nb_index.isDefinedFor(itemType)) {
                int i = Number.asSize(item, IndexError::new);
                if (i < 0) { i += self.size(); }
                return sq_item(self, i);
            }
            // else if item is a PySlice { ... }
            else
                throw Abstract.indexTypeError(self, item);
        } catch (ClassCastException e) {
            throw PyObjectUtil.typeMismatch(s, TYPE);
        }
    }

    static void ass_subscript(PyObject s, PyObject item, PyObject value)
            throws Throwable {
        try {
            PyList self = (PyList) s;
            PyType itemType = item.getType();
            if (Slot.nb_index.isDefinedFor(itemType)) {
                int i = Number.asSize(item, IndexError::new);
                if (i < 0) { i += self.size(); }
                sq_ass_item(self, i, value);
            }
            // else if item is a PySlice { ... }
            else
                throw Abstract.indexTypeError(self, item);
        } catch (ClassCastException e) {
            throw PyObjectUtil.typeMismatch(s, TYPE);
        }
    }

}
