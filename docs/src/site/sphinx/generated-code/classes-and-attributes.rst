..  generated-code/classes-and-attributes.rst


Classes and Attributes
######################

Access to Attributes: A Trivial Case
************************************

Motivating Example
==================

Consider the simple statement ``a = C().x``,
where we construct an object and access a member.
We have not yet explored how to define classes and create instances,
but it is worth looking at the code CPython produces:

..  code-block:: python

    >>> dis.dis(compile("a = C().x", '<test>', 'exec'))
      1           0 LOAD_NAME                0 (C)
                  2 CALL_FUNCTION            0
                  4 LOAD_ATTR                1 (x)
                  6 STORE_NAME               2 (a)
                  8 LOAD_CONST               0 (None)
                 10 RETURN_VALUE

Since construction is just a function call,
the only opcode we lack is ``LOAD_ATTR``.
(We'll implement ``STORE_ATTR`` too.)

However, what kind of function is it that we call?
The answer, of course, is that it is the ``type`` object ``C`` itself.
A ``PyType`` must therefore be callable,
returning a new instance of the associated Java class
(or of a sub-class) implementing the Python type.

First, however,
we turn to the definition of a Python object with attribute operations.

A hand-crafted class will serve the purpose,
like the one we used in :ref:`a-specialised-callable`,
if it returns an object capable of "attribute access".
And we can instantiate it from Java without the associated ``PyType``.


Slots for Attribute Access
==========================

The type must implement two new slots comparable to those in CPython.
These are ``tp_getattro`` and ``tp_setattro``.
These slots name the attribute by an object (always a ``str``).
We will strongly type the name argument as ``PyUnicode``,
adding the following slots and signatures:

..  code-block:: java

    enum Slot {
        ...
        tp_getattro(Signature.GETATTRO, null, "__getattr__"), //
        tp_setattro(Signature.SETATTRO, null, "__setattr__"), //
        ...
        enum Signature implements ClassShorthand {
            ...
            GETATTRO(O, S, U), // (getattrofunc) tp_getattro
            SETATTRO(V, S, U, O); // (setattrofunc) tp_setattro

``U`` is a shorthand for ``PyUnicode``, defined in ``ClassShorthand``.
We also add ``tp_getattro`` and ``tp_setattro`` slots to ``PyType``,
but no new apparatus is required in that class.

CPython has slots in addition to the ones we reproduce here,
called ``tp_getattr`` and ``tp_setattr`` (no "o"),
that name the attribute by a ``char *``.
These are the legacy of a now deprecated earlier approach
where attribute access had only that signature.
We only implement the newer form.

As usual, the new slots are wrapped in abstract methods
so that we may call them from Java,
including from the implementation of the opcodes.
In CPython,
the abstract method wrapping ``tp_getattro`` is like this:

..  code-block:: c
    :emphasize-lines: 6, 12-13

    PyObject *
    PyObject_GetAttr(PyObject *v, PyObject *name)
    {
        PyTypeObject *tp = Py_TYPE(v);

        if (!PyUnicode_Check(name)) {
            PyErr_Format(PyExc_TypeError,
                         "attribute name must be string, not '%.200s'",
                         name->ob_type->tp_name);
            return NULL;
        }
        if (tp->tp_getattro != NULL)
            return (*tp->tp_getattro)(v, name);
        if (tp->tp_getattr != NULL) {
            const char *name_str = PyUnicode_AsUTF8(name);
            if (name_str == NULL)
                return NULL;
            return (*tp->tp_getattr)(v, (char *)name_str);
        }
        PyErr_Format(PyExc_AttributeError,
                     "'%.50s' object has no attribute '%U'",
                     tp->tp_name, name);
        return NULL;
    }

Note that CPython falls back on the legacy slot ``tp_getattr``.
We will discuss the ``PyUnicode_Check(name)`` shortly.

There's also a ``PyObject_GetAttrString(PyObject *v, const char *name)``
that accepts the name as a ``char *`` and tries the legacy slot first.
If the legacy slot is not defined,
it creates a temporary ``str`` object and calls ``PyObject_GetAttr``.


.. _candidate-getattr:

Candidate ``getAttr``
---------------------

As usual, we take advantage of Java to choose a shorter name.
A candidate ``getAttr`` (strongly typed to ``PyUnicode``) is:

..  code-block:: java

        /** Python {@code o.name}. */
        static PyObject getAttr(PyObject o, PyUnicode name)
                throws AttributeError, Throwable {
            PyType t = o.getType();
            try {
                return (PyObject) t.tp_getattro.invokeExact(o, name);
            } catch (EmptyException e) {
                throw noAttributeError(o, name);
            }
        }

In fact, this is a slight over-simplification
as we shall see in :ref:`getattribute-and-getattr`.

In most contexts,
we expect it to be known statically that the name is a ``PyUnicode``,
and so the type check that CPython feels necessary may be avoided.
In particular,
this applies to the implementation of the ``LOAD_ATTR`` opcode:

..  code-block:: java

        PyObject eval() {
            ...
            // Cached references from code
            PyUnicode[] names = code.names;
            ...
                        case Opcode.LOAD_ATTR: // v.name
                            v = valuestack[sp - 1];
                            valuestack[sp - 1] =
                                    Abstract.getAttr(v, names[oparg]);
                            break;

The ``names`` array is known to be a ``PyUnicode[]``.
An alternative signature covers cases where the type of the name is not
known statically to be ``PyUnicode``.

..  code-block:: java

        static PyObject getAttr(PyObject o, PyObject name)
                throws Throwable {
            if (name instanceof PyUnicode) {
                return getAttr(o, (PyUnicode) name);
            } else {
                throw new TypeError(ATTR_MUST_BE_STRING_NOT, name);
            }
        }

A ``String`` case would be convenient when writing Java code,
but this is a trap when it comes to efficiency:
it involves making a ``PyUnicode`` every time we call it.
(The equivalent is avoided in CPython source for a good reason.)
We use a call to ``Py.str`` for ephemeral values
or constant interned in ``ID`` when built-in names are involved.

There is a ``setAttr`` to complement the candidate ``getAttr``,
with an easily-guessed implementation.


.. _a-custom-class-constructor:

A Custom Class with Attribute Access
====================================

A class exhibiting these slots,
and giving access to a single attribute ``x``,
is as follows:

..  code-block:: java
    :emphasize-lines: 10, 12, 21

        @SuppressWarnings("unused")
        private static class C implements PyObject {

            static final PyType TYPE =
                    PyType.fromSpec(new PyType.Spec("00C", C.class));

            @Override
            public PyType getType() { return TYPE; }

            PyObject x;         // Attribute for test

            static PyObject __getattribute__(C self, PyUnicode name)
                    throws Throwable {
                String n = name.toString();
                if ("x".equals(n) && self.x != null)
                    return self.x;
                else
                    throw Abstract.noAttributeError(self, name);
            }

            static void __setattr__(C self, PyUnicode name, PyObject value)
                    throws Throwable {
                String n = name.toString();
                if ("x".equals(n))
                    self.x = value;
                else
                    throw Abstract.noAttributeError(self, name);
            }

            static PyObject __new__(PyType cls, PyTuple args, PyDict kwargs) {
                return new C();
            }
        }

There is no proper attribute look-up going on.
We test the name, and if it is exactly "x",
then we get or set the attribute.
We call it all like this (in a JUnit test),
exercising the abstract method ``getAttr``
that also supports the ``LOAD_ATTR`` opcode:

..  code-block:: java

        @Test
        void abstract_attr() throws Throwable {
            PyObject c = new C();
            Abstract.setAttr(c, Py.str("x"), Py.val(42));
            PyObject result = Abstract.getAttr(c, Py.str("x"));
            assertEquals(Py.val(42), result);
        }


Creating an Instance with ``__new__``
=====================================

Note that the test class ``C`` has a method ``__new__``.
During class creation,
a handle to this is placed into slot ``tp_new``.
We need to arrange that ``C.TYPE`` be callable,
in other words that a ``PyType`` should be callable in general in Python.

To this end, we give ``PyType`` a ``__call__`` method
that will invoke the method in that type object's ``__new__``.
A simplified version of that (enough to make the example work) is:

..  code-block:: java

    class PyType implements PyObject {
        //...

        static PyObject __call__(PyType type, PyTuple args, PyDict kwargs)
                throws Throwable {
            try {
                // Create the instance with given arguments.
                MethodHandle n = type.tp_new;
                PyObject o = (PyObject) n.invokeExact(type, args, kwargs);
                //...
                return o;
            } catch (EmptyException e) {
                throw new TypeError("cannot create '%.100s' instances",
                        type.name);
            }
        }

The elided code in the body of ``__call__``
deals with calling ``__init__`` on the new object,
and also with the possibility that the type being called is ``type``,
in which case,
if there is only one argument,
we are implementing the ``type()`` built-in function.
We may easily test this for ``C``:

..  code-block:: java

    class PyByteCode6 {
        // ...
        @Test
        void call_type_noargs() throws Throwable {
            PyObject c = Callables.call(C.TYPE);
            assertEquals(c.getType(), C.TYPE);
        }


A Glance up the Mountain
========================

Common built-ins do not provide for attributes added by client code,
that is,
they have no instance dictionary.
However, they have attributes, which may be data or methods.

In the case of methods,
getting one from an instance may create a binding
(a sort of Curried function) that is a new callable object.
Not only that, the slots we rely on extensively (like ``nb_sub``)
are also exposed as methods (e.g. ``__sub__``)
that can be called on instances or types.

The manner of defining and accessing these will bring us to
the rich topic of the :ref:`Descriptors`
inserted in the dictionary of a type when it is created.
These in turn are all inseparable from sub-classing and inheritance.

In order to experiment with even the most familiar attributes
of built-in types therefore,
we must greatly strengthen class and object creation
in our toy implementation.

Suddenly, we have a significant climb ahead,
but we will get there by small steps.
One might suppose that logical route is to
develop the means to define classes,
then to create and use their instances,
but then we have to go a long way before anything can be tested.
Instead,
we shall get instances of various kinds into existence by any means,
get them working in some of the ways necessary to the language,
and then ask how we can arrive at that state properly.


Class and Instance Improvements
*******************************

In this section we improve (but do not expect to perfect)
class and instance creation from Python.
This is a complex subject,
too complex to surmount in a single leap,
but we need to start somewhere.

Orientation
===========

Currently (from ``evo3``) we have built-in types,
implemented as Java classes,
for which the type objects are created by initialising the Java class.
Somewhere in the static initialisation of the implementation class,
we call ``PyType.fromSpec`` or the equivalent.
(The static initialisation of ``PyType`` itself
creates ``type`` and ``object``.)

We can create instances of these built-in types by:

* calling the constructor from Java (e.g. in a unit test);
* calling runtime support methods like ``Py.str()`` or ``Py.val()``
  when building a code object; or
* executing object-creating opcodes (like ``MAKE_FUNCTION``)
  or doing arithmetic.

For test purposes, we need to be able to create instances from Python,
as well as force them into existence from Java.
A start would be to be able to call ``int()`` or ``str()``,
to create instances of ``int`` and ``str``.
For this, we must define the ``__call__`` slot function in ``PyType``,
so that anything that is a ``type`` can be called to make an instance.

Then we would like to create *classes* in Python,
which is to say we would like to be able to create instances of ``type``.
One does not normally do this by calling ``type()``,
but it is quite possible to do so:

..  code-block:: python

    >>> C = type('C', (str,), {'a':"hello"})
    >>> C.__mro__
    (<class '__main__.C'>, <class 'str'>, <class 'object'>)
    >>> c.a
    'hello'

Normally though, one executes a ``class`` statement.


``__call__`` in ``PyType``
==========================

``PyType.__call__`` is actually fairly simple,
but it depends on two other new slots.
The body of this method invokes the new slot function ``__new__``,
which returns a new object,
followed optionally by ``__init__`` on the object itself.
``__new__`` must be defined or inherited
by all types we expect to instantiate this way.

..  code-block:: java

    class PyType implements PyObject {
        //...
        static PyObject __call__(PyType type, PyTuple args, PyDict kwargs)
                throws TypeError, Throwable {
            try {
                // Create the instance with given arguments.
                MethodHandle n = type.tp_new;
                PyObject o = (PyObject) n.invokeExact(type, args, kwargs);
                // Check for special case type enquiry.
                if (isTypeEnquiry(type, args, kwargs)) { return o; }
                // As __new__ may be user-defined, check type as expected.
                PyType oType = o.getType();
                if (oType.isSubTypeOf(type)) {
                    // Initialise the object just returned (if necessary).
                    if (Slot.tp_init.isDefinedFor(oType))
                        oType.tp_init.invokeExact(o, args, kwargs);
                }
                return o;
            } catch (EmptyException e) {
                throw new TypeError("cannot create '%.100s' instances",
                        type.name);
            }
        }
        //...
    }

The code must take into account that ``type`` is itself a type,
but the call ``type(x)`` enquires the type of ``x``,
rather than being a constructor.
(The call ``type(name, bases, dict)`` does construct a ``type`` however.)
This difference is detected from the number of arguments by
``isTypeEnquiry(type, args, kwargs)``.
We follow CPython in placing the test after ``__new__`` is invoked.
``type.__new__`` performs both functions.


Slots ``tp_new`` and ``tp_init``
================================

The slot ``tp_init`` (for ``__init__``) holds no surprises:
it basically looks like ``tp_call``,
but returns ``void``.

The Python special method ``__new__``,
for which ``tp_new`` is the slot,
leads to an (effectively) static method.
It therefore does not have the "self type" in its signature,
but ``T``, standing for ``Class<? extends PyType>``.

..  code-block:: java

    enum Slot {
        ...
        tp_init(Signature.INIT), //
        tp_new(Signature.NEW), //

        enum Signature implements ClassShorthand {
            ...
            INIT(V, S, TUPLE, DICT), // (initproc) tp_init
            NEW(O, T, TUPLE, DICT); // (newfunc) tp_new
            ...
        }
        ...
    }

These are easily defined,
but the hard work is to add them to every built-in type.
Let's start with ``int``.


``__new__`` in ``PyLong``
=========================

The CPython code behind ``int()`` is quite complicated,
and not very interesting in the present context,
except to say that it tries the ``nb_int`` and ``nb_index`` slots,
in the case of single arguments ``int(x)``,
and a conversion from text for string-like objects.
For the purpose of exploration,
the Very Slow Jython code base implements a subset of the functionality.

The following attempt at ``PyLong.__new__`` gives an idea,
but it does not deal with Python subclasses of ``int``.
The lines highlighted invoke, directly or indirectly,
a constructor of ``PyLong``.

..  code-block:: java
    :emphasize-lines: 8, 15

    static PyObject __new__(PyType type, PyTuple args, PyDict kwargs)
            throws Throwable {
        PyObject x = null, obase = null;

        // ... argument processing to x, obase

        if (obase == null)
            return Number.asLong(x);
        else {
            int base = Number.asSize(obase, null);
            if ((base != 0 && base < 2) || base > 36)
                throw new ValueError(
                        "int() base must be >= 2 and <= 36, or 0");
            else if (x instanceof PyUnicode)
                return new PyLong(new BigInteger(x.toString(), base));
            // else if ... support for bytes-like objects
            else
                throw new TypeError(NON_STR_EXPLICIT_BASE);
        }
    }

The type object for ``int`` can be called from Java.
The test ``PyByteCode6.intFrom__new__`` does this
for a few of the possible constructor calls.

..  code-block:: java

    class PyByteCode6 {
        // ...
        @Test
        void intFrom__new__() throws Throwable {
            PyType intType = PyLong.TYPE;
            // int()
            PyObject result = Callables.call(intType);
            assertEquals(PyLong.ZERO, result);
            // int(42)
            result = Callables.call(intType, Py.tuple(Py.val(42)), null);
            assertEquals(Py.val(42), result);
            // int("2c", 15)
            PyTuple args = Py.tuple(Py.str("2c"), Py.val(15));
            result = Callables.call(intType, args, null);
            assertEquals(Py.val(42), result);
        }

In order to make the ``int`` constructor accessible from Python,
and the same for ``float`` (not detailed here),
it is only necessary to make them attributes of the ``builtins`` module:

..  code-block:: java

    class BuiltinsModule extends JavaModule implements Exposed {

        BuiltinsModule() {
            super("builtins");
            // ...
            add(ID.intern("float"), PyFloat.TYPE);
            add(ID.intern("int"), PyLong.TYPE);
        }

Now we can execute the following fragment within ``PyByteCode6``:

..  code-block:: python

    # Exercise the constructors for int and float
    i = int(u)
    x = float(i)
    y = float(u)
    j = int(y)

and this is done for a range of arguments ``u`` and ``i``.


``__new__`` in ``PyType`` (Provisional)
=======================================

When we invoke the ``__call__`` special method of ``PyType``,
and the target ``PyType`` is ``type`` itself,
the ``__new__`` special method of ``type`` is invoked,
and we create a new type from the arguments supplied.
This convoluted situation needs careful thought,
based on successively approximating the class build process.

Consider the apparently trivial sequence:

..  code-block:: python

    C = type('C', (), {})
    c = C()

Here we call the constructor of ``type`` objects
to create a class called ``"C"``,
that for sanity's sake we assign to the variable ``C``.
This is to say we call ``type.__call__``,
and this in turn calls ``type.__new__``.
The arguments are the name, a tuple of bases and a name space
that would ordinarily be the result of executing
the body of a class definition.

We have seen ``type.__call__`` already,
but a provisional ``type.__new__`` runs like this:

..  code-block:: java

     static PyObject __new__(PyType metatype, PyTuple args, PyDict kwds)
                throws Throwable {

            // Special case: type(x) should return type(x)
            if (isTypeEnquiry(metatype, args, kwds)) {
                return args.get(0).getType();
            }

            // ... Process arguments to bases, name, namespace ...

            // Specify using provided material
            Spec spec = new Spec(name).namespace(namespace);
            for (PyObject t : bases) {
                if (t instanceof PyType)
                    spec.base((PyType) t);
            }

            return PyType.fromSpec(spec);
     }

After the clause where ``__new__`` checks to see if this is a type enquiry,
it creates a specification for the type,
and a type from that.
In CPython, ``type_new`` is 523 lines long,
so it is likely we have missed a few details,
but we do actually get a type object from this.

In the Python snippet,
we go on to call that type object to get an instance.
That works too, iof we don't look too hard.

One delicate question is how to choose the (Java) implementation class
of the new type.
For a built-in type we construct the ``Spec`` with a knowledge of the
implementation class.
The new type is a Python subclass of each of its bases
(or if that tuple is empty, as it is in the example, just of ``object``).
It must also be a Java sub-class of their implementation types,
so that any methods implemented in Java are applicable to it.
This creates a constraint on the selection of bases
that is the Java parallel to the dreaded "layout conflict".

Assuming ``PyBaseObject`` appears to work for this simple case,
but it doesn't get us far:
``C`` should have an instance dictionary and
``PyBaseObject`` (i.e. ``object``) doesn't.
The correct Java class is one that all the bases may extend,
and which may also have an instance dictionary (or slots, or both).


.. _instance-dictionary:

The Instance Dictionary
=======================

Support in ``PyObject``
-----------------------

It will be a frequent need to get the instance dictionary (in Java) from
a Python object, to look up attributes in it.
This includes the case where the object is a type object.
So we're going to add that facility to the interface ``PyObject``.

Now, it would be a mistake here to promise a reference to
a fully-functional ``PyDict``.
Some types of object (and ``type`` is one of them),
insist on controlling access to their members.
(``PyType`` has a lot of re-computing to do when attributes change,
so it needs to know when that happens.)
Although every ``type`` object has a ``__dict__`` member,
it is not as permissive as those found in objects of user-defined type.

..  code-block:: python

    >>> class C: pass

    >>> (c:=C()).__dict__['a'] = 42
    >>> c.a
    42
    >>> type(c.__dict__)
    <class 'dict'>
    >>> type(C.__dict__)
    <class 'mappingproxy'>
    >>> C.__dict__['a'] = 42
    Traceback (most recent call last):
      File "<pyshell#489>", line 1, in <module>
        C.__dict__['a'] = 42
    TypeError: 'mappingproxy' object does not support item assignment

We therefore need to accommodate instance "dictionaries"
that are ``dict``\-like, but may be a read-only proxy to the real,
potentially modifiable dictionary.
We now redefine:

..  code-block:: java

    interface PyObject {

        /** The Python {@code type} of this object. */
        PyType getType();

        /**
         * The dictionary of the instance, (not necessarily a Python
         * {@code dict} or writable.
         */
        default Map<PyObject, PyObject> getDict(boolean create) {
            return null;
        }
    }

An object may implement this additional method
by handing out an actual instance dictionary (a ``dict``),
or a proxy that manages access.

..  code-block:: java

    class PyDict extends LinkedHashMap<PyObject, PyObject>
            implements Map<PyObject, PyObject>, PyObject {
        // ...


The slightly clumsy ``create`` argument is intended to allow objects
that create their dictionary lazily,
to defer creation until a client intends to write something in it.


Read-only Dictionary (``PyType``)
---------------------------------

Where we need to ensure that a mapping handed out by an object
is not modified by the client,
we may use an implementation of ``getDict()`` that wraps it,
for example, if ``dict`` is the instance dictionary:

..  code-block:: java

        @Override
        public Map<PyObject, PyObject> getDict(boolean create) {
            return Collections.unmodifiableMap(dict);
        }

We do this in ``PyType``,
to prevent clients updating the dictionary directly.
The ``PyObject`` interface is public API,
as public as the ``__dict__`` attribute,
and therefore we cannot rely on clients to be well-behaved,
remembering to police their own use of the dictionary,
and triggering re-computation of the ``PyType`` after changes.

(It also prevents ``object.__setattr__`` being applied to a type,
since ``PyBaseObject.__setattr__`` uses this API.)

While built-in types generally do not allow attribute setting,
many user-defined instances of ``PyType`` allow it.
We can manage this because we give ``PyType`` a custom ``__setttr__``,
that inspects the flag that determines this kind of mutability,
and has private access to the type dictionary.
*All* type objects have to respond to changes to special methods
in their dictionary,
by updating type slots
and notifying sub-classes of (potentially) changed inheritance.
The custom ``__setttr__`` also makes sure that happens.

Since we have already strayed a long way into
the discussion of attribute access,
we turn to that next.


The Mechanism of Attribute Access
*********************************

.. _getattribute-and-getattr:

``__getattribute__`` and ``__getattr__``
========================================

In the :ref:`candidate-getattr`,
we showed a simplified ``getAttr()`` sufficient for the example just past.
It matches the CPython code, but CPython is hiding a trick.

All built-in types have a function in their ``tp_getattro`` slot
that consults the type of target object
and the instance dictionary of the object,
in the order defined by the Python data model.
This function is ``PyObject_GenericGetAttr`` in ``object.c``.

Before Python 2.2,
the way in which a type defined in Python could customise attribute access,
was to define the special method ``__getattr__``.
That method would be called when the built-in mechanism
failed to resolve the attribute name.
At Python 2.2,
the language introduced ``__getattribute__`` as a way to give
types defined in Python complete control over attribute access,
but the hook ``__getattr__`` continues to be supported.
For the history of the change, consult `Attribute access in Python 2.2`_,
and earlier versions.

The `Python Data Model`_ states that
"if the class also defines ``__getattr__()``,
the latter will not be called unless ``__getattribute__()`` either
calls it explicitly or raises an ``AttributeError``".
However, there is no sign of this in either ``object.__getattribute__``
(which is the C function ``PyObject_GenericGetAttr``)
or ``PyObject_GetAttr`` (in the abstract API).

In CPython,
this is accomplished at almost no cost by setting ``tp_getattro``,
in classes defined in Python,
to a function ``slot_tp_getattr_hook`` that calls ``__getattribute__``,
and if that raises ``AttributeError`` catches it, and calls ``__getattr__``.
The CPython trick is that this hook method,
on finding that ``__getattr__`` is not defined,
replaces itself in the slot with a simplified version ``slot_tp_getattro``
that only looks for ``__getattribute__``.
If ``__getattr__`` is subsequently added to a class,
the re-working of the type slots that takes place re-inserts
``slot_tp_getattr_hook``.

Built-in classes in CPython fill the ``tp_getattro`` slot directly,
usually with ``PyObject_GenericGetAttr``,
or by inheritance.
The slot is exposed as ``__getattribute__``.

..  _Attribute access in Python 2.2:
    https://docs.python.org/3/whatsnew/2.2.html#attribute-access

.. _Python Data Model:
    https://docs.python.org/3/reference/datamodel.html


A Java Approach
---------------

In CPython, So the mechanism we are looking for
has been cleverly folded into the slot function.
We could do this in the ``MethodHandle``,
but we choose a greater transparency at the cost of an extra slot.
We shall have two slots ``tp_getattribute`` and ``tp_getattro``,
and put the mechanism for choosing between them in ``Abstract.getAttr``:

..  code-block:: java

        static PyObject getAttr(PyObject o, PyUnicode name)
                throws AttributeError, Throwable {
            // Decisions are based on type of o (that of name is known)
            PyType t = o.getType();
            try {
                // Invoke __getattribute__.
                return (PyObject) t.tp_getattribute.invokeExact(o, name);
            } catch (EmptyException | AttributeError e) {
                try {
                    // Not found or not defined: fall back on __getattr__.
                    return (PyObject) t.tp_getattro.invokeExact(o, name);
                } catch (EmptyException ignored) {
                    // __getattr__ not defined, original exception stands.
                    if (e instanceof AttributeError) { throw e; }
                    throw noAttributeError(o, name);
                }
            }
        }


This will carry no run-time cost where ``__getattribute__`` succeeds,
and only a small one if it raises ``AttributeError``
but ``__getattr__`` not defined.

The difference in slots from CPython
will be visible wherever ``tp_getattro`` is referenced directly.
In ported code, it should probably be converted to ``tp_getattribute``,
and it may be appropriate to fall back to ``tp_getattro`` in the code.
All the examples of this are in the implementation of attribute access.
In our implementation,
the ``Slot``\s are not API, and so this is an internal matter.


.. _descriptors-in-concept:

Descriptors in Concept
======================

There is a long discussion of the different types of descriptor
in the architecture section :ref:`Descriptors`.
The short version is that a descriptor is
an object that defines the slot function ``__get__``,
and may also define ``__set__`` and ``__delete__``.
If it also defines ``__set__`` or ``__delete__`` it is a data descriptor.

A descriptor may appear in the dictionary of a type object.

When looking for an attribute on an object,
the dictionary of the type object is consulted first.
The type may, in the end, supply a simple value for the attribute,
as when a variable or constant defined in the class body
is referenced via the instance.
However,
the search for an attribute via the type will often find a descriptor,
and the ``__get__``, ``__set__`` or ``__delete__``,
according to the action requested,
will then take control of the getting, setting or deletion.

Most attributes of built-in types are mediated this way,
and it is especially important in the way that methods are bound
before being called.
That descriptors are executed in the course of attribute access,
is critical to a full understanding of the implementations of
``__getattribute__``, ``__setattr__`` and ``__delattr__``
in the coming sections.


.. _object-getattribute:

Implementing ``object.__getattribute__``
========================================

The standard implementation of ``__getattribute__`` is in ``PyBaseObject``.
The special function (type slot) it produces
is inherited by almost all built-in and user-defined classes.
It fills the type slot ``tp_getattribute``.

The code speaks quite well for itself.
It is adapted from the CPython ``PyObject_GenericGetAttr`` in ``object.c``,
taking account of our different approach to error handling,
and with the removal of some efficiency tricks.
There is some delicacy around which exceptions should be caught,
and the next source consulted,
and which should put a definitive end to the attempt.

..  code-block:: java

    class PyBaseObject extends AbstractPyObject {
        //...

        static PyObject __getattribute__(PyObject obj, PyUnicode name)
                throws AttributeError, Throwable {

            PyType objType = obj.getType();
            MethodHandle descrGet = null;

            // Look up the name in the type (null if not found).
            PyObject typeAttr = objType.lookup(name);
            if (typeAttr != null) {
                // Found in the type, it might be a descriptor
                PyType typeAttrType = typeAttr.getType();
                descrGet = typeAttrType.tp_descr_get;
                if (typeAttrType.isDataDescr()) {
                    // typeAttr is a data descriptor so call its __get__.
                    try {
                        return (PyObject) descrGet.invokeExact(typeAttr,
                                obj, objType);
                    } catch (AttributeError | Slot.EmptyException e) {
                        /*
                         * Not found via descriptor: fall through to try the
                         * instance dictionary, but not the descriptor
                         * again.
                         */
                        descrGet = null;
                    }
                }
            }

            /*
             * At this stage: typeAttr is the value from the type, or a
             * non-data descriptor, or null if the attribute was not found.
             * It's time to give the object instance dictionary a chance.
             */
            Map<PyObject, PyObject> dict = obj.getDict(false);
            PyObject instanceAttr;
            if (dict != null && (instanceAttr = dict.get(name)) != null) {
                // Found something
                return instanceAttr;
            }

            /*
             * The name wasn't in the instance dictionary (or there wasn't
             * an instance dictionary). We are now left with the results of
             * look-up on the type.
             */
            if (descrGet != null) {
                // typeAttr may be a non-data descriptor: call __get__.
                try {
                    return (PyObject) descrGet.invokeExact(typeAttr, obj,
                            objType);
                } catch (Slot.EmptyException e) {}
            }

            if (typeAttr != null) {
                // The attribute obtained from the type is the value.
                return typeAttr;
            }

            // All the look-ups and descriptors came to nothing :(
            throw Abstract.noAttributeError(obj, name);
        }



.. _object-setattr:

Implementing ``object.__setattr__``
===================================

The approach to ``__delattr__`` and ``__setattr__``
differs from the implementation in CPython.
``__delattr__`` definitely exists separately in the Python data model,
but in CPython both compete for the ``tp_setattro`` slot.
CPython funnels both source-level operations (assignment and deletion)
into ``PyObject_SetAttr`` with deletion indicated by a ``null``
as the value to be assigned.
When definitions of ``__delattr__`` and ``__setattr__`` exist in Python,
CPython's synthetic type-slot function chooses which to call
based on the nullity of the value.

Our approach reflects a design policy of one special function per type slot.
It simplifies the logic (fewer if statements),
although it means a little more code as we have separate methods.

The standard implementation of ``__setattr__`` is as follows:

..  code-block:: java

    class PyBaseObject extends AbstractPyObject {
        //...

        static void __setattr__(PyObject obj, PyUnicode name,
                PyObject value) throws AttributeError, Throwable {

            // Accommodate CPython idiom that set null means delete.
            if (value == null) {
                // Do this to help porting. Really this is an error.
                __delattr__(obj, name);
                return;
            }

            // Look up the name in the type (null if not found).
            PyObject typeAttr = obj.getType().lookup(name);
            if (typeAttr != null) {
                // Found in the type, it might be a descriptor.
                PyType typeAttrType = typeAttr.getType();
                if (typeAttrType.isDataDescr()) {
                    // Try descriptor __set__
                    try {
                        typeAttrType.tp_descr_set.invokeExact(typeAttr, obj,
                                value);
                        return;
                    } catch (Slot.EmptyException e) {
                        // We do not catch AttributeError: it's definitive.
                        // Descriptor but no __set__: do not fall through.
                        throw Abstract.readonlyAttributeError(obj, name);
                    }
                }
            }

            /*
             * There was no data descriptor, so we will place the value in
             * the object instance dictionary directly.
             */
            Map<PyObject, PyObject> dict = obj.getDict(true);
            if (dict == null) {
                // Object has no dictionary (and won't support one).
                if (typeAttr == null) {
                    // Neither had the type an entry for the name.
                    throw Abstract.noAttributeError(obj, name);
                } else {
                    /*
                     * The type had either a value for the attribute or a
                     * non-data descriptor. Either way, it's read-only when
                     * accessed via the instance.
                     */
                    throw Abstract.readonlyAttributeError(obj, name);
                }
            } else {
                try {
                    // There is a dictionary, and this is a put.
                    dict.put(name, value);
                } catch (UnsupportedOperationException e) {
                    // But the dictionary is unmodifiable
                    throw Abstract.cantSetAttributeError(obj);
                }
            }
        }


.. _object-delattr:

Implementing ``object.__delattr__``
===================================

The standard ``object.__delattr__`` is not much different from
``object.__setattr__``.
If we find a data descriptor in the type,
we call its ``tp_descr_delete`` slot
in place of ``tp_descr_set`` in ``__setattr__``.
Not only have we a distinct slot for ``__delattr__`` in objects,
we have one for ``__delete__`` in descriptors too.

Note the way ``isDataDescr()`` is used
in both ``__setattr__`` and ``__delattr__``
in deciding whether to call the descriptor:
a descriptor is a data descriptor if it defines
*either* ``__set__`` or ``__delete__``.
It need not define both.

It is therefore possible to find a data descriptor in the type,
and then find the necessary slot empty.
This is raises an ``AttributeError``:
we should not go on to try the instance dictionary.
In these circumstances CPython also raises an attribute error,
but from within the slot function (and with less helpful text).

..  code-block:: java

    class PyBaseObject extends AbstractPyObject {
        //...
        static void __delattr__(PyObject obj, PyUnicode name)
                throws AttributeError, Throwable {

            // Look up the name in the type (null if not found).
            PyObject typeAttr = obj.getType().lookup(name);
            if (typeAttr != null) {
                // Found in the type, it might be a descriptor.
                PyType typeAttrType = typeAttr.getType();
                if (typeAttrType.isDataDescr()) {
                    // Try descriptor __delete__
                    try {
                        typeAttrType.tp_descr_delete.invokeExact(typeAttr,
                                obj);
                        return;
                    } catch (Slot.EmptyException e) {
                        // We do not catch AttributeError: it's definitive.
                        // Data descriptor but no __delete__.
                        throw Abstract.mandatoryAttributeError(obj, name);
                    }
                }
            }

            /*
             * There was no data descriptor, so we will remove the name from
             * the object instance dictionary directly.
             */
            Map<PyObject, PyObject> dict = obj.getDict(true);
            if (dict == null) {
                // Object has no dictionary (and won't support one).
                if (typeAttr == null) {
                    // Neither has the type an entry for the name.
                    throw Abstract.noAttributeError(obj, name);
                } else {
                    /*
                     * The type had either a value for the attribute or a
                     * non-data descriptor. Either way, it's read-only when
                     * accessed via the instance.
                     */
                    throw Abstract.readonlyAttributeError(obj, name);
                }
            } else {
                try {
                    // There is a dictionary, and this is a delete.
                    PyObject previous = dict.remove(name);
                    if (previous == null) {
                        // A null return implies it didn't exist
                        throw Abstract.noAttributeError(obj, name);
                    }
                } catch (UnsupportedOperationException e) {
                    // But the dictionary is unmodifiable
                    throw Abstract.cantSetAttributeError(obj);
                }
            }
        }



.. _type-getattribute:

Implementing ``type.__getattribute__``
======================================

The type object gets its own definition of ``__getattribute__``,
slightly different from that in ``object``,
and found in ``PyType.__getattribute__``.
We highlight the differences here.

Types have types, called the meta-type.
This occasions a change of variable names, even where the code is the same:
where in ``PyBaseObject`` we had ``obj``, in ``PyType`` we write ``type``,
and where we had ``typeAttr``, we write ``metaAttr``.

..  code-block:: java
    :emphasize-lines: 36, 38-51

    class PyType implements PyObject {
        //...
        static PyObject __getattribute__(PyType type, PyUnicode name)
                throws AttributeError, Throwable {

            PyType metatype = type.getType();
            MethodHandle descrGet = null;

            // Look up the name in the type (null if not found).
            PyObject metaAttr = metatype.lookup(name);
            if (metaAttr != null) {
                // Found in the metatype, it might be a descriptor
                PyType metaAttrType = metaAttr.getType();
                descrGet = metaAttrType.tp_descr_get;
                if (metaAttrType.isDataDescr()) {
                    // metaAttr is a data descriptor so call its __get__.
                    try {
                        return (PyObject) descrGet.invokeExact(metaAttr,
                                type, metatype);
                    } catch (AttributeError | Slot.EmptyException e) {
                        /*
                         * Not found via descriptor: fall through to try the
                         * instance dictionary, but not the descriptor
                         * again.
                         */
                        descrGet = null;
                    }
                }
            }

            /*
             * At this stage: metaAttr is the value from the meta-type, or a
             * non-data descriptor, or null if the attribute was not found.
             * It's time to give the type's instance dictionary a chance.
             */
            PyObject attr = type.lookup(name);
            if (attr != null) {
                // Found in this type. Try it as a descriptor.
                try {
                    /*
                     * Note the args are (null, this): we respect
                     * descriptors in this step, but have not forgotten we
                     * are dereferencing a type.
                     */
                    return (PyObject) attr.getType().tp_descr_get
                            .invokeExact(attr, (PyObject) null, type);
                } catch (Slot.EmptyException e) {
                    // We do not catch AttributeError: it's definitive.
                    // Not a descriptor: the attribute itself.
                    return attr;
                }
            }

            /*
             * The name wasn't in the type dictionary. We are now left with
             * the results of look-up on the meta-type.
             */
            if (descrGet != null) {
                // metaAttr may be a non-data descriptor: call __get__.
                try {
                    return (PyObject) descrGet.invokeExact(metaAttr, type,
                            metatype);
                } catch (Slot.EmptyException e) {}
            }

            if (metaAttr != null) {
                // The attribute obtained from the meta-type is the value.
                return metaAttr;
            }

            // All the look-ups and descriptors came to nothing :(
            throw Abstract.noAttributeError(type, name);
        }

As with regular objects,
the first step is to acceess the type (that is the meta-type),
and if we find a data descriptor, act on it.
The second option is again to look in the instance (that is, the type),
but here we use ``type.lookup(name)``, in place of a dictionary look-up,
and must also be ready to find a descriptor rather than a plain value.

If we find a descriptor, we call it with arguments ``(null, type)``.
A descriptor called so will most often return itself,
making this the same as retrieving the plain value,
but an exception is the descriptor of a class method
(see :ref:`PyClassMethodDescr`),
which returns the method bound to the type.


.. _type-setattr:

Implementing ``type.__setattr__``
=================================

The definition of `type.__setattr__``
is also slightly different from that in ``object``.
First we must deal with the possibility that
the type does not allow its attributes to be changed.
Most built-in types are in that category,
while most classes defined in Python (sub-classes of ``object``)
do allow this.

..  code-block:: java
    :emphasize-lines: 14-15, 23, 35, 51

    class PyType implements PyObject {
        //...
        static void __setattr__(PyType type, PyUnicode name, PyObject value)
                throws AttributeError, Throwable {

            // Accommodate CPython idiom that set null means delete.
            if (value == null) {
                // Do this to help porting. Really this is an error.
                __delattr__(type, name);
                return;
            }

            // Trap immutable types
            if (!type.flags.contains(Flag.MUTABLE))
                throw Abstract.cantSetAttributeError(type);

            // Force name to actual str , not just a sub-class
            if (name.getClass() != PyUnicode.class) {
                name = Py.str(name.toString());
            }

            // Check to see if this is a special name
            boolean special = isDunderName(name);

            // Look up the name in the meta-type (null if not found).
            PyObject metaAttr = type.getType().lookup(name);
            if (metaAttr != null) {
                // Found in the meta-type, it might be a descriptor.
                PyType metaAttrType = metaAttr.getType();
                if (metaAttrType.isDataDescr()) {
                    // Try descriptor __set__
                    try {
                        metaAttrType.tp_descr_set.invokeExact(metaAttr,
                                type, value);
                        if (special) { type.updateAfterSetAttr(name); }
                        return;
                    } catch (Slot.EmptyException e) {
                        // We do not catch AttributeError: it's definitive.
                        // Descriptor but no __set__: do not fall through.
                        throw Abstract.readonlyAttributeError(type, name);
                    }
                }
            }

            /*
             * There was no data descriptor, so we will place the value in
             * the object instance dictionary directly.
             */
            // Use the privileged put
            type.dict.put(name, value);
            if (special) { type.updateAfterSetAttr(name); }
        }

As in ``object.__setattr__``,
the logic looks for and acts on a data descriptors found in the meta-type,
and then moves to the instance dictionary of the type.
Things are made simpler by the fact that a type always has a dictionary,
and we already know that we are allowed to modify it.

Following the re-definition of any special function,
the type must be given the chance to re-compute internal data structures,
in particular, the affected type slots.


.. _type-delattr:

Implementing ``type.__delattr__``
=================================

There is nothing to write concerning ``type.__delattr__``
that is not already covered in :ref:`object-delattr`
and :ref:`type-setattr`.





Class Creation with Descriptors
*******************************



Integrating the Parts
*********************

Defining a Simple Class
=======================

Class definition turns out to begin with function definition:

..  code-block:: python

    >>> dis.dis(compile("class C : pass", '<test>', 'exec'))
      1           0 LOAD_BUILD_CLASS
                  2 LOAD_CONST               0 (<code object C at ... >)
                  4 LOAD_CONST               1 ('C')
                  6 MAKE_FUNCTION            0
                  8 LOAD_CONST               1 ('C')
                 10 CALL_FUNCTION            2
                 12 STORE_NAME               0 (C)
                 14 LOAD_CONST               2 (None)
                 16 RETURN_VALUE

    Disassembly of <code object C at ...>:
      1           0 LOAD_NAME                0 (__name__)
                  2 STORE_NAME               1 (__module__)
                  4 LOAD_CONST               0 ('C')
                  6 STORE_NAME               2 (__qualname__)
                  8 LOAD_CONST               1 (None)
                 10 RETURN_VALUE


We already have everything we need for this trivial example,
except for the new opcode ``LOAD_BUILD_CLASS``.
This opcode simply pushes the function ``__builtins__.__build_class__``,
that by default is in the ``builtins`` module.

The next instructions define a *function* object ``C``,
whose body is the *class* body
(defined by the code object also displayed).

Finally,
the function ``__build_class__`` is called with just two arguments:
the function object just defined, and the name of ``C``.
There is not much to the function body in this trivial case,
but it will get executed (not exactly called as a function),
within ``__build_class__``.
What it leaves behind in its ``locals()``,
essentially populates the dictionary of the type.


A First Approximation to ``__build_class__``
============================================

``__build_class__`` is quite complicated,
and quite likely we cannot implement it fully
with the type system as it stands.



..  code-block:: java


..  code-block:: java

