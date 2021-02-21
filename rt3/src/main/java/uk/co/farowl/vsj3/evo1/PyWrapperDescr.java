package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import uk.co.farowl.vsj3.evo1.Exposed.Getter;
import uk.co.farowl.vsj3.evo1.PyType.Flag;
import uk.co.farowl.vsj3.evo1.Slot.MethodKind;

/**
 * A {@link Descriptor} for a particular definition <b>in Java</b> of
 * one of the special methods of the Python data model (such as
 * {@code __sub__}). The type also appears as
 * {@code <class 'wrapper_descriptor'>}.
 * <p>
 * The owner of the descriptor is the Python type providing the
 * definition. Type construction places a {@code PyWrapperDescr} in the
 * dictionary of the defining {@link PyType}, against a key that is the
 * "dunder name" of the special method it wraps. (This does not preclude
 * client code moving it around afterwards!)
 * <p>
 * The {@code PyWrapperDescr} provides a {@code MethodHandle} for the
 * defining method. In every Python type where a {@code PyWrapperDescr}
 * appears as the attribute value corresponding to a special method, the
 * handle will fill the corresponding type slot. This may happen because
 * the type is the defining type, by inheritance, or by insertion of the
 * {@code PyWrapperDescr} as an attribute of the type. (In the last
 * case, the signature of the wrapped and destination slots must match.)
 */
/*
 * Difference from CPython: In CPython, a PyWrapperDescr is created
 * because the slot at the corresponding offset in the PyTypeObject of
 * the owning Python type is filled, statically or by PyType_FromSpec.
 *
 * In this implementation, we create a PyWrapperDescr as an attribute
 * because the Java implementation of the owning type defines a method
 * with that slot's name. Then we fill the slot because the type has an
 * attribute with the matching name. The result should be the same but
 * the process is more regular.
 */
abstract class PyWrapperDescr extends Descriptor {

    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("wrapper_descriptor",
                    MethodHandles.lookup()).flagNot(Flag.BASETYPE));

    /**
     * The {@link Slot} ({@code enum}) describing the generic
     * characteristics the special method of which
     * {@link Descriptor#objclass} provides a particular implementation.
     */
    final Slot slot;

    /**
     * A handles for the particular implementations of a special method
     * being wrapped. The method type of each is that of
     * {@link #slot}{@code .signature}.
     */
    final MethodHandle[] wrapped;

    /**
     * Construct a slot wrapper descriptor, identifying by a method
     * handle the implementation method for the {@code slot} in
     * {@code objclass}.
     *
     * @param objclass the class declaring the special method
     * @param slot for the generic special method
     * @param wrapped a handle to an implementation of that slot
     */
    // Compare CPython PyDescr_NewClassMethod in descrobject.c
    @Deprecated // XXX Accommodate array of handles instead
    PyWrapperDescr(PyType objclass, Slot slot, MethodHandle wrapped) {
        super(TYPE, objclass, slot.methodName);
        this.slot = slot;
        this.wrapped = new MethodHandle[] {wrapped};
    }

    /**
     * Construct a slot wrapper descriptor, identifying by an array of
     * method handles the implementation methods for the {@code slot} in
     * {@code objclass}.
     *
     * @param objclass the class declaring the special method
     * @param slot for the generic special method
     * @param wrapped handles to the implementation of that slot
     */
    // Compare CPython PyDescr_NewClassMethod in descrobject.c
    PyWrapperDescr(PyType objclass, Slot slot, MethodHandle[] wrapped) {
        super(TYPE, objclass, slot.methodName);
        this.slot = slot;
        this.wrapped = wrapped;
    }

// /**
// * Invoke the wrapped method handle, having arranged the arguments
// * as expected by a slot. When we create sub-classes of
// * {@code PyWrapperDescr} to handle different slot signatures, this
// * is method that accepts arguments in a generic way (from the
// * interpreter, say) and adapts them to the specific needs of the
// * method handle {@link #wrapped}.
// *
// * @param self target object of the method call
// *
// * @param args of the method call
// * @param kwargs of the method call
// * @return result of the method call
// * @throws Throwable from the implementation of the special method
// */
// // Compare CPython wrapperdescr_raw_call in descrobject.c
// @Deprecated
// abstract Object callWrapped(Object self, PyTuple args,
// PyDict kwargs) throws Throwable;

    /**
     * Invoke the wrapped method handle, having arranged the arguments
     * as expected by a slot. When we create sub-classes of
     * {@code PyWrapperDescr} to handle different slot signatures, this
     * is method that accepts arguments in a generic way (from the
     * interpreter, say) and adapts them to the specific needs of the
     * method handle {@link #wrapped}.
     *
     * @param self target object of the method call
     * @param index of self amongst accepted implementations
     * @param args of the method call
     * @param kwargs of the method call
     * @return result of the method call
     * @throws Throwable from the implementation of the special method
     */
    // Compare CPython wrapperdescr_raw_call in descrobject.c
    abstract Object callWrapped(Object self, int index, PyTuple args,
            PyDict kwargs) throws Throwable;

    // Exposed attributes ---------------------------------------------

    // CPython get-set table (to convert to annotations):
    // private GetSetDef wrapperdescr_getset[] = {
    // {"__doc__", (getter)wrapperdescr_get_doc},
    // {"__qualname__", (getter)descr_get_qualname},
    // {"__text_signature__",
    // (getter)wrapperdescr_get_text_signature},
    // {0}
    // };

    @Getter
    // Compare CPython wrapperdescr_get_doc in descrobject.c
    protected Object __doc__() {
        return PyType.getDocFromInternalDoc(slot.methodName, slot.doc);
    }

    @Getter
    // Compare CPython wrapperdescr_get_text_signature in descrobject.c
    protected Object __text_signature__() {
        return PyType.getTextSignatureFromInternalDoc(slot.methodName,
                slot.doc);
    }

    // Special methods ------------------------------------------------

    // CPython type object (to convert to special method names):
    // PyType PyWrapperDescr_Type = {
    // PyVar_HEAD_INIT(&PyType_Type, 0)
    // "wrapper_descriptor",
    // sizeof(PyWrapperDescr),
    // (reprfunc)wrapperdescr_repr, /* tp_repr */
    // (ternaryfunc)wrapperdescr_call, /* tp_call */
    // PyObject_GenericGetAttr, /* tp_getattro */
    // Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC |
    // Py_TPFLAGS_METHOD_DESCRIPTOR, /* tp_flags */
    // descr_traverse, /* tp_traverse */
    // descr_methods, /* tp_methods */
    // descr_members, /* tp_members */
    // wrapperdescr_getset, /* tp_getset */
    // (descrgetfunc)wrapperdescr_get, /* tp_descr_get */
    // };

    // Compare CPython wrapperdescr_repr in descrobject.c
    @SuppressWarnings("unused")
    private Object __repr__() {
        return descrRepr("slot wrapper");
    }

    // Compare CPython wrapperdescr_get in descrobject.c
    @Override
    protected Object __get__(Object obj, PyType type) {
        if (obj == null)
            /*
             * obj==null indicates the descriptor was found on the
             * target object itself (or a base), see CPython
             * type_getattro in typeobject.c
             */
            return this;
        else {
            // Return callable binding this and obj
            check(obj);
            return new PyMethodWrapper(this, obj);
        }
    }

    // Compare CPython wrapperdescr_call in descrobject.c
    protected Object __call__(PyTuple args, PyDict kwargs)
            throws TypeError, Throwable {
        // Split the leading element self from args
        int argc = args.value.length;
        if (argc < 1) {
            throw new TypeError(DESCRIPTOR_NEEDS_ARGUMENT, name,
                    objclass.name);
        }
        Object self = args.value[0];
        args = new PyTuple(args.value, 1, argc - 1);

        // Work out how to call this descriptor on that object
        Class<?> selfClass = self.getClass();
        int index = objclass.indexAccepted(selfClass);

        PyType selfType = PyType.of(self);

        // XXX does existence of a match => sub-class?

        // Make sure that the first argument is acceptable as 'self'

        if (index < 0
                || !Abstract.recursiveIsSubclass(selfType, objclass)) {
            throw new TypeError(DESCRIPTOR_REQUIRES, name,
                    objclass.name, selfType.name);
        }

        return callWrapped(self, index, args, kwargs);
    }

    // Plumbing ------------------------------------------------------

    /**
     * Check that no positional or keyword arguments are supplied. This
     * is for use when implementing
     * {@link #callWrapped(Object, PyTuple, PyDict)}.
     *
     * @param args positional argument tuple to be checked
     * @param kwargs to be checked
     * @throws TypeError if {@code kwargs} is not {@code null} or empty
     */
    final protected void checkNoArgs(PyTuple args, PyDict kwargs)
            throws TypeError {
        if (args.value.length != 0)
            throw new TypeError(TAKES_NO_ARGUMENTS, name,
                    args.value.length);
        else if (kwargs != null && kwargs.isEmpty())
            throw new TypeError(TAKES_NO_KEYWORDS, name);
    }

    /**
     * Check the number of positional arguments and that no keywords are
     * supplied. This is for use when implementing
     * {@link #callWrapped(Object, PyTuple, PyDict)}.
     *
     * @param args positional argument tuple to be checked
     * @param expArgs expected number of positional arguments
     * @param kwargs to be checked
     * @throws TypeError if {@code kwargs} is not {@code null} or empty
     */
    final protected void checkArgs(PyTuple args, int expArgs,
            PyDict kwargs) throws TypeError {
        int n = args.value.length;
        if (n != expArgs)
            throw new TypeError(TAKES_ARGUMENTS, name, expArgs, n);
        else if (kwargs != null && kwargs.isEmpty())
            throw new TypeError(TAKES_NO_KEYWORDS, name);
    }

    /**
     * Check the number of positional arguments and that no keywords are
     * supplied. This is for use when implementing
     * {@link #callWrapped(Object, PyTuple, PyDict)}.
     *
     * @param args positional argument tuple to be checked
     * @param minArgs minimum number of positional arguments
     * @param maxArgs maximum number of positional arguments
     * @param kwargs to be checked
     * @throws TypeError if {@code kwargs} is not {@code null} or empty
     */
    final protected void checkArgs(PyTuple args, int minArgs,
            int maxArgs, PyDict kwargs) throws TypeError {
        int n = args.value.length;
        if (n < minArgs || n > maxArgs)
            throw new TypeError(TAKES_ARGUMENTS, name,
                    String.format("from %d to %d", minArgs, maxArgs),
                    n);
        else if (kwargs != null && kwargs.isEmpty())
            throw new TypeError(TAKES_NO_KEYWORDS, name);
    }

    private static final String TAKES_NO_ARGUMENTS =
            "wrapper %s() takes no arguments (%d given)";
    private static final String TAKES_ARGUMENTS =
            "wrapper %s() takes %s arguments (%d given)";
    private static final String TAKES_NO_KEYWORDS =
            "wrapper %s() takes no keyword arguments";

    /**
     * {@code WrapperDef} represents one or more methods of a Java class
     * that are to be exposed as a single special method of an
     * {@code object}. The exporting class provides a definitions for
     * that method that appear here as {@code Method}s with different
     * signatures.
     */
    static class WrapperDef {

        /** The special method being defined. */
        final Slot slot;
        /** Collects the methods declared. */
        final List<Method> methods = new ArrayList<>(1);

        /**
         * Obvious constructor
         *
         * @param name of attribute.
         */
        WrapperDef(Slot slot) {
            this.slot = slot;
        }

        /**
         * Add a method implementation. (A test that the signature
         * matches the slot follows when we construct the
         * {@link PyWrapperDescr}.)
         *
         * @param method to add to {@link #methods}
         */
        void add(Method method) {
            methods.add(method);
        }

        /**
         * Create a {@code PyWrapperDescr} from this definition. Note
         * that a definition describes the methods as declared, and that
         * there may be any number. This method matches them to the
         * supported implementations.
         *
         * @param objclass Python type that owns the descriptor
         * @param lookup authorisation to access fields
         * @return descriptor for access to the field
         * @throws InterpreterError if the method type is not supported
         */
        PyWrapperDescr createDescr(PyType objclass, Lookup lookup)
                throws InterpreterError {

            // Acceptable methods can be coerced to this signature
            MethodType slotType = slot.getType();
            final int L = slotType.parameterCount();
            assert (L >= 1);

            /*
             * There could be any number of candidates in the
             * implementation. An implementation method could match
             * multiple accepted implementations of the type (e.g.
             * Number matching Long and Integer).
             */
            LinkedList<MethodHandle> candidates = new LinkedList<>();
            for (Method m : methods) {
                // Convert m to a handle (if L args and accessible)
                try {
                    MethodHandle mh = lookup.unreflect(m);
                    if (mh.type().parameterCount() == L)
                        addOrdered(candidates, mh);
                } catch (IllegalAccessException e) {
                    throw new InterpreterError(e,
                            "cannot get method handle for '%s' in '%s'",
                            m, objclass.implClass);
                }
            }

            // We will try to create a handle for each implementation
            // XXX accommodating static/class here but should we?
            final boolean instanceMethod =
                    slot.signature.kind == MethodKind.INSTANCE;
            final int N = instanceMethod ? objclass.operandClasses.length : 1;
            MethodHandle[] wrapped = new MethodHandle[N];

            // Fill the wrapped array with matching method handles
            for (int i = 0; i < N; i++) {
                Class<?> acceptedClass = objclass.operandClasses[i];
                /*
                 * Fill wrapped[i] with the method handle where the
                 * first parameter is the most specific match for class
                 * accepted[i].
                 */
                // Try the candidate method until one matches
                for (MethodHandle mh : candidates) {
                    if (!instanceMethod || mh.type().parameterType(0)
                            .isAssignableFrom(acceptedClass)) {
                        try {
                            // must have the expected signature
                            wrapped[i] = mh.asType(slotType);
                            break;
                        } catch (WrongMethodTypeException wmte) {
                            // Wrong number of args or cannot cast.
                            throw methodSignatureError(mh);
                        }
                    }
                }

                // We should have a value in each of wrapped[]
                if (wrapped[i] == null) {
                    throw new InterpreterError(
                            "'%s.%s' not defined for %s", objclass.name,
                            slot.methodName, objclass.operandClasses[i]);
                }
            }

            return slot.signature.makeSlotWrapper(objclass, slot,
                    wrapped);
        }

        /**
         * Create a method handle on the implementation method,
         * verifying that the method type produced is compatible with
         * the {@link #slot}. The method may be {@code null}, signifying
         * a method was not defined, in which case the returned handle
         * is {@code null}.
         *
         * @param lookup authorisation to access fields
         * @param m implementing method
         * @return method handle on {@code m}
         */
        private MethodHandle unreflect(Lookup lookup, Method m) {
            try {
                /*
                 * This handle reflects the method signature and the
                 * object operates on should be consistent because it
                 * implements the descriptor's objclass.
                 */
                MethodHandle mh = lookup.unreflect(m);
                try {
                    /*
                     * The call site that invokes the handle will have a
                     * signature matching the slot, therefore add a cast
                     * to the method handle obtained from the method.
                     */
                    return mh.asType(slot.getType());
                } catch (WrongMethodTypeException wmte) {
                    // Wrong number of args or cannot cast.
                    throw methodSignatureError(mh);
                }
            } catch (IllegalAccessException e) {
                throw new InterpreterError(e,
                        "cannot get method handle for '%s'", m);
            }
        }

        /**
         * Insert a {@code MethodHandle h} into a list, such that every
         * handle in the list, of which the first parameter type is
         * assignable from the first parameter type of {@code h}, will
         * appear after {@code h} in the list. If there are none such,
         * {@code h} is added at the end. The resulting list is
         * partially ordered, and has the property that, in a forward
         * search for a handle applicable to a given class, the most
         * specific match is found first.
         *
         * @param list to add h into
         * @param h to insert/add
         */
        private void addOrdered(LinkedList<MethodHandle> list,
                MethodHandle h) {
            // Type of first parameter of h
            Class<?> c = h.type().parameterType(0);
            // We'll work forwards a more general type is found
            ListIterator<MethodHandle> iter = list.listIterator(0);
            while (iter.hasNext()) {
                MethodHandle i = iter.next();
                Class<?> d = i.type().parameterType(0);
                if (d.isAssignableFrom(c)) {
                    /*
                     * d is more general than c (i is more general than
                     * h): back up and position just before i.
                     */
                    iter.previous();
                    break;
                }
            }
            // Insert h where the iterator stopped. Could be the end.
            iter.add(h);
        }

        /** Convenience function to compose error in unreflect(). */
        private InterpreterError methodSignatureError(MethodHandle mh) {
            return new InterpreterError(UNSUPPORTED_SIG,
                    slot.methodName, mh.type(), slot.opName);
        }

        private static final String UNSUPPORTED_SIG =
                "method %.50s has wrong signature %.50s for slot %s";

        @Override
        public String toString() {
            return String.format("WrapperDef(%s[%d])", slot.methodName,
                    methods.size());
        }

        /** Method name or null (for toString()). */
        private static String mn(Method m) {
            return m == null ? "" : m.getName();
        }
    }

}