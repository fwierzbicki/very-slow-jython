package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.HashMap;
import java.util.Map;

/**
 * This {@code enum} provides a set of structured constants that are
 * used to refer to the special methods of the Python data model.
 * <p>
 * Each constant creates a correspondence between its name, the (slot)
 * name in the {@code PyType} object (because it is the same), the type
 * of the {@code MethodHandle} every occurrence of that slot must
 * contain, and the conventional name by which the implementing class of
 * a type will refer to that method, if it offers an implementation. It
 * holds all the run-time system needs to know about the special method
 * in general, but not any information specific to a particular type.
 * <p>
 * In principle, any Python object may support all of the special
 * methods, through "slots" in the Python type object {@code PyType}.
 * These slots have identical names to the corresponding constant in
 * this {@code enum}. The "slots" in the Python type object hold
 * pointers ({@code MethodHandle}s) to their implementations in Java for
 * that type, which of course define the behaviour of instances in
 * Python. Where a special method is absent from the implementation of a
 * type, a default "empty" handle is provided from the {@code Slot}
 * constant.
 */
// Compare CPython struct wrapperbase in descrobject.h
// also typedef slotdef and slotdefs[] table in typeobject.h
enum Slot {

    op_repr(Signature.UNARY), //
    op_hash(Signature.LEN), //
    op_call(Signature.CALL), //
    op_str(Signature.UNARY), //

    op_getattribute(Signature.GETATTR), //
    op_getattr(Signature.GETATTR), //
    op_setattr(Signature.SETATTR), //
    op_delattr(Signature.DELATTR), //

    op_lt(Signature.BINARY), //
    op_le(Signature.BINARY), //
    op_eq(Signature.BINARY), //
    op_ne(Signature.BINARY), //
    op_ge(Signature.BINARY), //
    op_gt(Signature.BINARY), //

    op_iter(Signature.UNARY), //
    op_next(Signature.UNARY), //

    op_get(Signature.DESCRGET), //
    op_set(Signature.SETITEM), //
    op_delete(Signature.DELITEM), //

    op_init(Signature.INIT), //
    op_new(Signature.NEW), //

    op_vectorcall(Signature.VECTORCALL), //

    op_neg(Signature.UNARY), //
    op_abs(Signature.UNARY), //

    // Binary ops: reflected form comes first so we can reference it.
    op_radd(Signature.BINARY, "+"), //
    op_rsub(Signature.BINARY, "-"), //
    op_rmul(Signature.BINARY, "*"), //
    op_rand(Signature.BINARY, "&"), //
    op_rxor(Signature.BINARY, "^"), //
    op_ror(Signature.BINARY, "|"), //

    op_add(Signature.BINARY, "+", op_radd), //
    op_sub(Signature.BINARY, "-", op_rsub), //
    op_mul(Signature.BINARY, "*", op_rmul), //
    op_and(Signature.BINARY, "&", op_rand), //
    op_xor(Signature.BINARY, "^", op_rxor), //
    op_or(Signature.BINARY, "|", op_ror), //

    /** Handle to {@code __bool__} with {@link Signature#PREDICATE} */
    op_bool(Signature.PREDICATE), //
    op_int(Signature.UNARY), //
    op_float(Signature.UNARY), //
    op_index(Signature.UNARY), //

    op_len(Signature.LEN), //

    op_contains(Signature.BINARY_PREDICATE), //

    op_getitem(Signature.BINARY), //
    op_setitem(Signature.SETITEM), //
    op_delitem(Signature.DELITEM);

    /** Method signature to match when filling this slot. */
    final Signature signature;
    /** Name of implementation method to bind to this slot. */
    final String methodName;
    /** Name of implementation method as {@link PyUnicode}. */
    // XXX Do not provoke type system by setting in constructor.
    PyUnicode name_strobj;
    /** Name to use in error messages */
    final String opName;
    /** Name to use in error messages */
    final String doc;
    /** Reference to field holding this slot in a {@link PyType} */
    final VarHandle slotHandle;
    /** Reference to field holding alternate slot in a {@link PyType} */
    final VarHandle altSlotHandle;

    /**
     * Constructor for enum constants.
     *
     * @param signature of the function to be called
     * @param opName symbol (such as "+")
     * @param methodName implementation method (e.g. "__add__")
     * @param alt alternate slot (e.g. "op_radd")
     */
    Slot(Signature signature, String opName, String methodName,
            Slot alt) {
        this.opName = opName == null ? name() : opName;
        this.methodName = dunder(methodName);
        this.signature = signature;
        this.slotHandle = Util.slotHandle(this);
        this.altSlotHandle = alt == null ? null : alt.slotHandle;
        // XXX Need something convenient as in CPython.
        this.doc = "Doc of " + this.name();
    }

    Slot(Signature signature) { this(signature, null, null, null); }

    Slot(Signature signature, String opName) {
        this(signature, opName, null, null);
    }

    Slot(Signature signature, String opName, Slot alt) {
        this(signature, opName, null, alt);
    }

    /** Compute corresponding double-underscore method name. */
    private String dunder(String methodName) {
        if (methodName != null)
            return methodName;
        else {
            String s = name();
            int i = s.indexOf('_');
            if (i == 2)
                s = "__" + s.substring(i + 1) + "__";
            return s;
        }
    }

    @Override
    public java.lang.String toString() {
        return "Slot." + name() + " ( " + methodName + signature.type
                + " ) [" + signature.name() + "]";
    }

    /**
     * Lookup by method name, returning {@code null} if it is not a
     * recognised name for any slot.
     *
     * @param name of a (possible) special method
     * @return the Slot corresponding, or {@code null}
     */
    static Slot forMethodName(String name) {
        return Util.getMethodNameTable().get(name);
    }

    /**
     * Get the name of the method that, by convention, identifies the
     * corresponding operation in the implementing class. This is not
     * the same as the slot name.
     *
     * @return conventional special method name.
     */
    String getMethodName() { return methodName; }

    /**
     * Return the invocation type of slots of this name.
     *
     * @return the invocation type of slots of this name.
     */
    MethodType getType() { return signature.empty.type(); }

    /**
     * Get the default that fills the slot when it is "empty".
     *
     * @return empty method handle for this type of slot
     */
    MethodHandle getEmpty() { return signature.empty; }

    /**
     * Test whether this slot is non-empty in the given type.
     *
     * @param t type to examine for this slot
     * @return true iff defined (non-empty)
     */
    boolean isDefinedFor(PyType t) {
        return (MethodHandle) slotHandle.get(t) != signature.empty;
    }

    /**
     * Return for a slot, a handle to the method in a given class that
     * implements it, or the default handle (of the correct signature)
     * that throws {@link EmptyException}.
     *
     * @param c target class
     * @param lookup authorisation to access {@code c}
     * @return handle to method in {@code c} implementing this slot.
     * @throws NoSuchMethodException slot method not found
     * @throws IllegalAccessException found but inaccessible
     */
    MethodHandle findInClass(Class<?> c, Lookup lookup)
            throws IllegalAccessException, NoSuchMethodException {
        switch (signature.kind) {
            case INSTANCE:
                return Util.findVirtualInClass(this, c, lookup);
            case CLASS:
            case STATIC:
                return Util.findStaticInClass(this, c, lookup);
            default:
                // Never happens
                return getEmpty();
        }
    }

    /**
     * Constructs a specialised version of {@link PyWrapperDescr} that
     * is able to arrange call arguments according to the pattern
     * expected by this slot.
     *
     * @param objclass the Python type declaring the special method
     * @param c the Java class declaring the special method
     * @param lookup authorisation to access {@code c}
     * @return a slot wrapper descriptor
     * @throws NoSuchMethodException slot method not found
     * @throws IllegalAccessException found but inaccessible
     */
    PyWrapperDescr makeDescriptor(PyType objclass, Class<?> c,
            Lookup lookup)
            throws IllegalAccessException, NoSuchMethodException {
        MethodHandle wrapped = findInClass(c, lookup);
        return signature.makeDescriptor(objclass, this, wrapped);
    }

    /**
     * Get the contents of this slot in the given type. Each member of
     * this {@code enum} corresponds to the name of a static method
     * which must also have the correct signature.
     *
     * @param t target type
     * @return current contents of this slot in {@code t}
     */
    MethodHandle getSlot(PyType t) {
        return (MethodHandle) slotHandle.get(t);
    }

    /**
     * Get the contents of the "alternate" slot in the given type. For a
     * binary operation this is the reflected operation.
     *
     * @param t target type
     * @return current contents of the alternate slot in {@code t}
     */
    MethodHandle getAltSlot(PyType t) {
        return (MethodHandle) altSlotHandle.get(t);
    }

    /**
     * Set the contents of this slot in the given type to the
     * {@code MethodHandle} provided.
     *
     * @param t target type object
     * @param mh handle value to assign
     */
    void setSlot(PyType t, MethodHandle mh) {
        if (mh == null || !mh.type().equals(getType()))
            throw slotTypeError(this, mh);
        slotHandle.set(t, mh);
    }

    /**
     * Set the contents of this slot in the given type to a
     * {@code MethodHandle} that calls the object given in a manner
     * appropriate to its type. This method is used when updating
     * setting the type slots of a new type from the new type's
     * dictionary, and when updating them after a change. The object
     * argument is then the entry found by lookup of this slot's name
     * (and may be {@code null} if no entry was found.
     * <p>
     * Where the object is a {@link PyWrapperDescr}, the wrapped method
     * handle will be set as by {@link #setSlot(PyType, MethodHandle)}.
     * The {@link PyWrapperDescr#slot} is not necessarily this slot.
     * Client Python code can enter any wrapper descriptor against the
     * name.
     *
     * @param t target type object
     * @param def object defining the handle to set (or {@code null})
     */
    // Compare CPython update_one_slot in typeobject.c
    void setSlot(PyType t, PyObject def) {
        MethodHandle mh;
        if (def == null) {
            // No definition available for the special method
            if (this == op_next) {
                // XXX We should special-case __next__
                /*
                 * In CPython, this slot is sometimes null=empty, and
                 * sometimes _PyObject_NextNotImplemented. PyIter_Check
                 * checks both, but PyIter_Next calls it without
                 * checking and a null would then cause a crash. We have
                 * EmptyException for a similar purpose.
                 */
            }
            mh = signature.empty;

        } else if (def instanceof PyWrapperDescr) {
            // Subject to certain checks, take wrapped handle.
            PyWrapperDescr wd = (PyWrapperDescr) def;
            if (wd.slot.signature == signature && t.isSubTypeOf(t)) {
                mh = wd.wrapped;
            } else {
                throw new MissingFeature(
                        "equivalent of the slot_* functions");
                // mh = signature.slotCalling(def);
            }

        } else if (def instanceof PyJavaFunction) {
            // We should be able to do this efficiently ... ?
            // PyJavaFunction func = (PyJavaFunction) def;
            if (this == op_next)
                throw new MissingFeature(
                        "special case caller for __new__ wrapper");
            throw new MissingFeature(
                    "Efficient handle from PyJavaFunction");
            // mh = signature.slotCalling(func);

        } else if (def == Py.None && this == op_hash) {
            throw new MissingFeature("special case __hash__ == None");
            // mh = PyObject_HashNotImplemented

        } else {
            throw new MissingFeature(
                    "equivalent of the slot_* functions");
            // mh = makeSlotHandle(wd);
        }

        slotHandle.set(t, mh);
    }

    /** The type of exception thrown by invoking an empty slot. */
    static class EmptyException extends Exception {

        // Suppression and stack trace disabled since singleton.
        EmptyException() { super(null, null, false, false); }
    }

    /**
     * Placeholder type, exclusively for use in slot signatures,
     * denoting the class defining the slot function actually bound into
     * the slot. See {@link MethodKind#INSTANCE}.
     */
    interface Self extends PyObject {}

    /**
     * Placeholder type, exclusively for use in slot signatures,
     * denoting {@code PyType} but signalling a class method. See
     * {@link MethodKind#CLASS}.
     */
    interface Cls extends PyObject {}

    /**
     * The kind of special method that satisfies this slot. Almost all
     * slots are satisfied by an instance method. __new__ is a static
     * method. In theory, we need class method as a type, but there are
     * no live examples.
     */
    enum MethodKind {
        /**
         * The slot is satisfied by Java instance method. The first
         * parameter type in the declared signature will have been the
         * placeholder {@code Self}. The operation slot signature will
         * have {@code PyObject} in that position. When we look up the
         * Java implementation we will look for a virtual method using a
         * method type that is the declared type with {@code Self}
         * removed. When called, the target object has a type assignable
         * to the receiving type, thanks to a checked cast. The result
         * is that the defining methods need not include a cast to their
         * type on the corresponding argument.
         */
        INSTANCE,
        /**
         * The slot is satisfied by Java static method. The first
         * parameter type in the declared signature will have been the
         * placeholder {@code Cls}. The operation slot signature will
         * have {@code PyType} in that position. When we look up the
         * Java implementation we will look for a static method using a
         * method type that is the declared type with {@code Cls}
         * replaced by {@code PyType}. When called, this type object is
         * a sub-type (or the same as) the type implemented by the
         * receiving type.
         */
        // At least, that's what would happen if we used it :/
        CLASS,
        /**
         * The slot is satisfied by Java static method. The first
         * parameter type in the declared signature will have been
         * something other than {@code Self} or {@code Cls}. The
         * operation slot signature will be the same. When we look up
         * the Java implementation we will look for a static method
         * using the method type as declared type.
         */
        STATIC;
    }

    /**
     * An enumeration of the acceptable signatures for slots in a
     * {@code PyType}. For each {@code MethodHandle} we may place in a
     * slot, we must know in advance the acceptable signature
     * ({@code MethodType}), and the slot when empty must contain a
     * handle with this signature to a method that will raise
     * {@link EmptyException}, Each {@code enum} constant here gives a
     * symbolic name to that {@code MethodType}, and provides an
     * {@code empty} handle.
     * <p>
     * Names are equivalent to {@code typedef}s provided in CPython
     * {@code Include/object.h}, but not the same. We do not need quite
     * the same signatures as CPython: we do not return integer status,
     * for example. Also, C-specifics like {@code Py_ssize_t} are echoed
     * in the C-API names but not here.
     */
    enum Signature implements ClassShorthand {

        /*
         * The makeDescriptor overrides returning anonymous sub-classes
         * of PyWrapperDescr are fairly ugly. However, sub-classes seem
         * to be the right solution, and defining them here keeps
         * information together that belongs together.
         */

        /**
         * The signature {@code (S)O}, for example {@link Slot#op_repr}
         * or {@link Slot#op_neg}.
         */
        UNARY(O, S) {

            @Override
            PyWrapperDescr makeDescriptor(PyType objclass, Slot slot,
                    MethodHandle wrapped) {
                return new PyWrapperDescr(objclass, slot, wrapped) {

                    @Override
                    PyObject callWrapped(PyObject self, PyTuple args,
                            PyDict kwargs) throws Throwable {
                        checkArgs(args, 0, kwargs);
                        return (PyObject) wrapped.invokeExact(self);
                    }
                };
            }
        },

        /**
         * The signature {@code (S,O)O}, for example {@link Slot#op_add}
         * or {@link Slot#op_getitem}.
         */
        BINARY(O, S, O) {

            @Override
            PyWrapperDescr makeDescriptor(PyType objclass, Slot slot,
                    MethodHandle wrapped) {
                return new PyWrapperDescr(objclass, slot, wrapped) {

                    @Override
                    PyObject callWrapped(PyObject self, PyTuple args,
                            PyDict kwargs) throws Throwable {
                        checkArgs(args, 1, kwargs);
                        return (PyObject) wrapped.invokeExact(self,
                                args.value[0]);
                    }
                };
            }
        },
        /**
         * The signature {@code (S,O,O)O}, used for {@link Slot#op_pow}.
         */
        // The signature {@code (S,O,O)O}, used for {@link Slot#op_pow}.
        // **
        TERNARY(O, S, O, O),

        /**
         * The signature {@code (S,O,TUPLE,DICT)O}, used for
         * {@link Slot#op_call}.
         */
        // u(self, *args, **kwargs)
        CALL(O, S, TUPLE, DICT) {

            @Override
            PyWrapperDescr makeDescriptor(PyType objclass, Slot slot,
                    MethodHandle wrapped) {
                return new PyWrapperDescr(objclass, slot, wrapped) {

                    @Override
                    PyObject callWrapped(PyObject self, PyTuple args,
                            PyDict kwargs) throws Throwable {
                        return (PyObject) wrapped.invokeExact(self,
                                args, kwargs);
                    }
                };
            }
        },

        // u(x, y, ..., a=z)
        VECTORCALL(O, S, OA, I, I, TUPLE),

        // Slot#op_bool
        PREDICATE(B, S),

        // Slot#op_contains
        BINARY_PREDICATE(B, S, O),

        // Slot#op_length, Slot#op_hash
        LEN(I, S),

        // (objobjargproc) Slot#op_setitem, Slot#op_set
        SETITEM(V, S, O, O),

        // (not in CPython) Slot#op_delitem, Slot#op_delete
        DELITEM(V, S, O),

        // (getattrofunc) Slot#op_getattr
        GETATTR(O, S, U),

        // (setattrofunc) Slot#op_setattr
        SETATTR(V, S, U, O),

        // (not in CPython) Slot#op_delattr
        DELATTR(V, S, U),

        // (descrgetfunc) Slot#op_get
        DESCRGET(O, S, O, T) {

            @Override
            PyWrapperDescr makeDescriptor(PyType objclass, Slot slot,
                    MethodHandle wrapped) {
                return new PyWrapperDescr(objclass, slot, wrapped) {

                    @Override
                    PyObject callWrapped(PyObject self, PyTuple args,
                            PyDict kwargs) throws Throwable {
                        checkArgs(args, 1, 2, kwargs);
                        PyObject[] a = args.value;
                        PyObject obj = a[0];
                        if (obj == Py.None) { obj = null; }
                        PyObject type = null;
                        if (a.length > 1 && type != Py.None) {
                            type = a[1];
                        }
                        if (type == null && obj == null) {
                            throw new TypeError(
                                    "__get__(None, None) is invalid");
                        }
                        return (PyObject) wrapped.invokeExact(self, obj,
                                (PyType) type);
                    }
                };
            }
        },

        /**
         * The signature {@code (S,O,TUPLE,DICT)V}, used for
         * {@link Slot#op_init}.
         */
        // (initproc) Slot#op_init
        INIT(V, S, TUPLE, DICT),

        // (newfunc) Slot#op_new
        NEW(O, T, TUPLE, DICT);

        /**
         * The signature was defined with this nominal method type,
         * which will often include a {@link Self} placeholder
         * parameter.
         */
        final MethodType type;
        /**
         * Whether instance, static or class method. This determines the
         * kind of lookup we must perform on the implementing class.
         */
        final MethodKind kind;
        /**
         * When we do the lookup, this is the method type we specify,
         * derived from {@link #type} according to {@link #kind}.
         */
        final MethodType methodType;
        /**
         * When empty, the slot should hold this handle. The method type
         * of this handle also tells us the method type by which the
         * slot must always be invoked, see {@link Slot#getType()}.
         */
        final MethodHandle empty;

        /**
         * Constructor to which we specify the signature of the slot,
         * with the same semantics as {@code MethodType.methodType()}.
         * Every {@code MethodHandle} stored in the slot (including
         * {@link Signature#empty}) must be of this method type.
         *
         * @param returnType that the slot functions all return
         * @param ptypes types of parameters the slot function takes
         */
        Signature(Class<?> returnType, Class<?>... ptypes) {
            // The signature is recorded exactly as given
            this.type = MethodType.methodType(returnType, ptypes);
            // In the type of this.empty, replace Self with PyObject.
            MethodType invocationType = Util.replaceSelf(this.type, O);
            // em = λ : throw Util.EMPTY
            // (with correct nominal return type for slot)
            MethodHandle em = MethodHandles
                    .throwException(returnType, EmptyException.class)
                    .bindTo(Util.EMPTY);
            // empty = λ u v ... : throw Util.EMPTY
            // (with correct parameter types for slot)
            this.empty = MethodHandles.dropArguments(em, 0,
                    invocationType.parameterArray());

            // Prepare the kind of lookup we should do
            Class<?> p0 = ptypes.length > 0 ? ptypes[0] : null;
            if (p0 == Self.class) {
                this.kind = MethodKind.INSTANCE;
                this.methodType = Util.dropSelf(this.type);
                // } else if (p0 == Cls.class) { ... CLASS ...
            } else {
                this.kind = MethodKind.STATIC;
                this.methodType = this.empty.type();
            }
        }

        /**
         * Return an instance of sub-class of {@link PyWrapperDescr},
         * specialised to the particular signature by overriding
         * {@link PyWrapperDescr#callWrapped(PyObject, PyTuple, PyDict)}.
         * Each member of {@code Signature} produces the appropriate
         * sub-class.
         *
         * @param objclass the class declaring the special method
         * @param slot for the generic special method
         * @param wrapped a handle to an implementation of that slot
         * @return a slot wrapper descriptor
         */
        // XXX should be abstract, but only when defined for each
        /* abstract */ PyWrapperDescr makeDescriptor(PyType objclass,
                Slot slot, MethodHandle wrapped) {
            return new PyWrapperDescr(objclass, slot, wrapped) {

                @Override
                PyObject callWrapped(PyObject self, PyTuple args,
                        PyDict kwargs) throws Throwable {
                    checkNoArgs(args, kwargs);
                    return (PyObject) wrapped.invokeExact(self);
                }
            };
        }
    }

    /**
     * Helper for {@link Slot#setSlot(PyType, MethodHandle)}, when a bad
     * handle is presented.
     *
     * @param slot that the client attempted to set
     * @param mh offered value found unsuitable
     * @return exception with message filled in
     */
    private static InterpreterError slotTypeError(Slot slot,
            MethodHandle mh) {
        String fmt = "%s not of required type %s for slot %s";
        return new InterpreterError(fmt, mh, slot.getType(), slot);
    }

    /**
     * Helpers for {@link Slot} and {@link Signature} that can be used
     * in the constructors.
     */
    private static class Util {

        /*
         * This is a class separate from Slot to solve problems with the
         * order of static initialisation. The enum constants have to
         * come first, and their constructors are called as they are
         * encountered. This means that other constants in Slot are not
         * initialised by the time the constructors need them.
         */
        private static final Lookup LOOKUP = MethodHandles.lookup();

        /** Single re-used instance of {@code Slot.EmptyException} */
        static final EmptyException EMPTY = new EmptyException();

        private static Map<String, Slot> methodNameTable = null;

        static Map<String, Slot> getMethodNameTable() {
            if (methodNameTable == null) {
                Slot[] slots = Slot.values();
                methodNameTable = new HashMap<>(2 * slots.length);
                for (Slot s : slots) {
                    methodNameTable.put(s.methodName, s);
                }
            }
            return methodNameTable;
        }

        /**
         * Helper for constructors at the point they need a handle for
         * their named field within a {@code PyType} class.
         */
        static VarHandle slotHandle(Slot slot) {
            Class<?> methodsClass = PyType.class;
            try {
                // The field has the same name as the enum
                return LOOKUP.findVarHandle(methodsClass, slot.name(),
                        MethodHandle.class);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new InterpreterError(e, "seeking slot %s in %s",
                        slot.name(), methodsClass.getSimpleName());
            }
        }

        /**
         * For a given slot, return a handle to the instance method in a
         * given class that implements it, or the default handle (of the
         * correct signature) that throws {@link EmptyException}.
         *
         * @param slot slot
         * @param c target class
         * @param lookup authorisation to access {@code c}
         * @return handle to method in {@code c} implementing {@code s}
         * @throws NoSuchMethodException slot method not found
         * @throws IllegalAccessException found but inaccessible
         */
        static MethodHandle findVirtualInClass(Slot slot, Class<?> c,
                Lookup lookup)
                throws IllegalAccessException, NoSuchMethodException {
            // PyBaseObject has a different approach
            if (c == PyBaseObject.class)
                return findInBaseObject(slot, lookup);
            // The method has the same name in every implementation
            String name = slot.getMethodName();
            Signature sig = slot.signature;
            assert sig.kind == MethodKind.INSTANCE;
            MethodType mt = sig.methodType;
            MethodHandle impl = lookup.findVirtual(c, name, mt);
            // The invocation type remains that of slot.empty
            return impl.asType(sig.empty.type());
        }

        /**
         * For a given slot, return a handle to the static method in a
         * given class that implements it, or the default handle (of the
         * correct signature) that throws {@link EmptyException}.
         *
         * @param slot slot
         * @param c class
         * @param lookup authorisation to access {@code c}
         * @return handle to method in {@code c} implementing {@code s}
         * @throws NoSuchMethodException slot method not found
         * @throws IllegalAccessException found but inaccessible
         */
        static MethodHandle findStaticInClass(Slot slot, Class<?> c,
                Lookup lookup)
                throws NoSuchMethodException, IllegalAccessException {
            // The method has the same name in every implementation
            String name = slot.getMethodName();
            Signature sig = slot.signature;
            assert sig.kind == MethodKind.STATIC;
            MethodType mt = sig.methodType;
            MethodHandle impl = lookup.findStatic(c, name, mt);
            // The invocation type remains that of slot.empty
            return impl.asType(sig.empty.type());
        }

        /**
         * For a given slot, return a handle to the method in
         * {@link PyBaseObject}{@code .class}, or the default handle (of
         * the correct signature) that throws {@link EmptyException}.
         * The declarations of (non-static) special methods in
         * {@code PyBaseObject} differ from those other implementation
         * classes in being declared Java {@code static}, with a
         * {@code PyObject self}.
         *
         * @param slot slot
         * @param lookup authorisation to access {@code PyBaseObject}
         * @return handle to method in {@code PyBaseObject} implementing
         *         {@code s}.
         * @throws NoSuchMethodException slot method not found
         * @throws IllegalAccessException found but inaccessible
         */
        static MethodHandle findInBaseObject(Slot slot, Lookup lookup)
                throws NoSuchMethodException, IllegalAccessException {
            // The method has this special method name.
            String name = slot.getMethodName();
            // The signature uses PyObject self to accept any object.
            Signature sig = slot.signature;
            MethodType mt = replaceSelf(sig.type, PyObject.class);
            // And the method is declared static (for that reason).
            MethodHandle impl =
                    lookup.findStatic(PyBaseObject.class, name, mt);
            assert impl.type() == sig.empty.type();
            return impl;
        }

        /**
         * Generate a method type in which an initial occurrence of the
         * {@link Self} class has been replaced by a specified class.
         * <p>
         * The type signature of method handles to special functions
         * (see {@link Signature}) are mostly specified with the dummy
         * type {@code Self} as the first type parameter. This indicates
         * that the special method is an instance method. However, the
         * method handle offered to the run-time must have the generic
         * ({@code PyObject}) in place of this dummy, since at the call
         * site, we only know the target is a {@code PyObject}.
         * <p>
         * Further, when seeking an implementation of the special method
         * that is static, the definition will usually have the defining
         * type in "self" position, and so {@code Lookup.findStatic}
         * must be provided a type signature in which the lookup class
         * appears as "self".
         *
         * (Exception: {@link PyBaseObject} has to be defined with
         * static methods and the type PyObject in "self" position, the
         * same as the run-time expects.)
         * <p>
         * This method provides a way to convert {@code Self} to a
         * specified type in a method type, either the one to which a
         * static implementation is expected to conform, or the one
         * acceptable to the run-time. A method type that does not have
         * {@code Self} at parameter 0 is returned unchanged.
         *
         * @param type signature with the dummy {@link Self}
         * @param c class to substitute for {@link Self}
         * @return signature after substitution
         */
        static MethodType replaceSelf(MethodType type, Class<?> c) {
            int n = type.parameterCount();
            if (n > 0 && type.parameterType(0) == Self.class)
                return type.changeParameterType(0, c);
            else
                return type;
        }

        /**
         * Generate a method type from which an initial occurrence of
         * the {@link Self} class has been removed.
         * <p>
         * The signature of method handles to special functions (see
         * {@link Signature}) are mostly specified with the dummy type
         * {@code Self} as the first type parameter. This indicates that
         * the special method is an instance method.
         * <p>
         * When defining the implementation of a special method that is
         * an instance method, which is most of them, it is convenient
         * to make it an instance method in Java. Then the method type
         * we supply to {@code Lookup.findVirtual} must omit the "self"
         * parameter. This method generates that method type.
         *
         * @param type signature with the dummy {@link Self}
         * @return signature after removal
         */
        static MethodType dropSelf(MethodType type) {
            int n = type.parameterCount();
            if (n > 0 && type.parameterType(0) == Self.class)
                return type.dropParameterTypes(0, 1);
            else
                return type;
        }
    }
}
