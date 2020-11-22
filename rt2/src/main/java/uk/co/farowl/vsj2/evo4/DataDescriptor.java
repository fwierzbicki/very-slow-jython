package uk.co.farowl.vsj2.evo4;

/** Base class of built-in data descriptors. */
abstract class DataDescriptor extends Descriptor {

    /**
     * Create the common part of {@code DataDescriptor} sub-classes.
     *
     * @param descrtype actual Python type of descriptor
     * @param objclass to which the descriptor applies
     * @param name of the attribute
     */
    DataDescriptor(PyType descrtype, PyType objclass, String name) {
        super(descrtype, objclass, name);
    }

    /**
     * The {@code __set__} special method of the Python descriptor
     * protocol, implementing {@code obj.name = value}. In general,
     * {@code obj} must be of type {@link #objclass}.
     *
     * @param obj object on which the attribute is sought
     * @param value to assign (not {@code null})
     * @throws Throwable from the implementation of the setter
     */
    // Compare CPython *_set methods in descrobject.c
    abstract void __set__(PyObject obj, PyObject value)
            throws TypeError, Throwable;

    /**
     * The {@code __delete__} special method of the Python descriptor
     * protocol, implementing {@code del obj.name}. In general,
     * {@code obj} must be of type {@link #objclass}.
     *
     * @param obj object on which the attribute is sought
     * @throws Throwable from the implementation of the deleter
     */
    // Compare CPython *_set in descrobject.c with NULL
    abstract void __delete__(PyObject obj) throws TypeError, Throwable;

    /**
     * {@code descr.__set__(obj, value)} has been called on this
     * descriptor. We must check that the descriptor applies to the type
     * of object supplied as the {@code obj} argument. From Python,
     * anything could be presented, but when we operate on it, we'll be
     * assuming the particular {@link #objclass} type.
     *
     * @param obj target object (argument to {@code __set__})
     * @throws TypeError if descriptor doesn't apply to {@code obj}
     */
    // Compare CPython descr_setcheck in descrobject.c
    protected void checkSet(PyObject obj) throws TypeError {
        PyType objType = obj.getType();
        if (!objType.isSubTypeOf(objclass)) {
            throw new TypeError(DESCRIPTOR_DOESNT_APPLY, name,
                    objclass.name, objType.name);
        }
    }

    /**
     * {@code descr.__delete__(obj)} has been called on this descriptor.
     * We must check that the descriptor applies to the type of object
     * supplied as the {@code obj} argument. From Python, anything could
     * be presented, but when we operate on it, we'll be assuming the
     * particular {@link #objclass} type.
     *
     * @param obj target object (argument to {@code __delete__})
     */
    // Compare CPython descr_setcheck in descrobject.c
    protected void checkDelete(PyObject obj) throws TypeError {
        PyType objType = obj.getType();
        if (!objType.isSubTypeOf(objclass)) {
            throw new TypeError(DESCRIPTOR_DOESNT_APPLY, name,
                    objclass.name, objType.name);
        }
    }

    /**
     * Create a {@link TypeError} with a message along the lines "cannot
     * delete attribute N from 'T' objects" involving the name N of this
     * attribute and the type T which is {@link Descriptor#objclass}
     * {@code value}, e.g. "cannot delete attribute <u>f_trace_lines</u>
     * from '<u>frame</u>' objects".
     *
     * @return exception to throw
     */
    protected TypeError cannotDeleteAttr() {
        String msg =
                "cannot delete attribute %.50s from '%.50s' objects";
        return new TypeError(msg, name, objclass.getName());
    }

    /**
     * Create a {@link TypeError} with a message along the lines "N must
     * be set to T, not a X object" involving the name N of this
     * attribute, any descriptive phrase T and the type X of
     * {@code value}, e.g. "<u>__dict__</u> must be set to <u>a
     * dictionary</u>, not a '<u>list</u>' object".
     *
     * @param kind expected kind of thing
     * @param value provided to set this attribute in some object
     * @return exception to throw
     */
    protected TypeError attrMustBe(String kind, PyObject value) {
        return Abstract.attrMustBe(name, kind, value);
    }
}
