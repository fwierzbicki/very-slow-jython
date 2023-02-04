..  architecture/object-implementation.rst

.. _object-implementation:

Object Implementation 
*********************


.. note:: This section still partly reflects the VSJ2 ``PyObject`` approach,
    not the plain-``Object`` perspective of VSJ3 we now think correct.
    This section is under edit into a definitive statement
    taking the plain-objects perspective of VSJ3.


This chapter describes how we implement the operations on Python objects
corresponding to:

* the special methods in the `Python Data Model`_
  (such as ``__hash__`` and ``__add__``).
* named methods defined for the type
  (such as ``str.replace`` and ``float.hex``).
* named attributes defined for the type
  (such as ``complex.real`` and ``types.FunctionType.__globals__``).

We have most to write about types implemented in Java,
since the mechanisms for types defined in Python are specified
by the language.
The process of finding these methods and fields
and making them accessible from Python
is called *exposure*.


..  _object-methods:

Special Methods in Java
=======================

Special methods can customise the behaviour of objects
involved in almost any construct we may encounter in Python source.
Compiled code mostly invokes them through the abstract API and
pointers detailed in the chapter on :ref:`type-slots`.

Special methods define the operations needed:

* to interpret byte code,
* to support the abstract object API,
* to implement the built-in functions in Java, and (quite likely)
* to support the same operations compiled to JVM code.

Filling the Type Slots
----------------------

Special methods defined in the Java implementation of an object
take distinctive names selected from
those defined in the `Python Data Model`_.
Their names in Java will be the same as they are in Python:
``__repr__``, ``__hash__``, ``__add__``, ``__getattribute__``, and so on.

The run-time exposure process
finds them by reflection and treats them specially.
The values in the ``enum Slot`` each describe a particular type slot:
the name of the special method from which it is to be filled,
and the Java ``MethodType`` of the slot it fills.
Each special method defined in a given built-in type
results in a slot wrapper descriptor in the dictionary of that type.
The ``MethodHandle`` that fills the type slot
is copied from that descriptor.

The sequence ``Slot.values()`` drives the search
and finally the filling of the type slots.
Note that this is the mirror image of the process in CPython,
where the type slot is filled statically with a pointer to
the implementing function,
and type construction creates a descriptor for it.

The Java visibility of methods in an implementation class is
places no restriction on exposure to Python.
Each implementation class supplies a lookup object to ``PyType``,
granting access to its methods and fields during construction.
It is quite possible to make methods and fields Java-``private``,
and have them exposed to Python on our own terms.

A type may have multiple accepted implementations.
It will have multiple ``Operations`` objects in its ``PyType``.
In that case, exposure still creates
one slot wrapper descriptor for each special method,
but each descriptor holds several method handles,
covering the set of accepted implementations.
These handles are then used to populate
the matching slot in each ``Operations`` object.

Inheritance of Slots
--------------------

Type slots receive handles from inherited special methods,
not just those defined by the type being exposed,
because the lookup of each ``Slot`` method name is made along the MRO.
If a special method not defined at all,
the slot is filled so that it throws an ``EmptyException`` when invoked.

In order that the inherited method handles be applicable to
the inheriting type,
the Java implementation of the inheriting type
must be assignment compatible with the ``self`` argument of
one of the handles in the inherited descriptor.
Often this will be because the implementation of the inheriting type
Java-extends the implementation of its Python base.

We may also meet this criterion if the inherited method
is ``static`` and declares its ``self`` argument to be ``Object``.
This is the strategy we use to make methods from ``PyBaseObject``,
the class that defines Python ``object``,
applicable to any Java class.

A third way to meet it is that the implementation of the base
explicitly provides implementations for the inheriting type.
This option is only available if the specific inheritance is known in advance.
We use it to support the inheritance of ``int`` methods into ``bool``,
even though the only implementation type of ``bool`` is ``java.lang.Boolean``.
Thus we find amongst the implementation methods of ``int``:

..  code-block:: java

    class PyLongMethods {
        // ...
        static Object __int__(PyLong self) { return self.value; }
        static Object __int__(BigInteger self) { return self; }
        static Object __int__(Integer self) { return self; }
        static Object __int__(Boolean self) { return (self ? 1 : 0); }

This code is generated by ``java_object_gen.py`` using ``PyLong.py``.


Example of ``tuple``
--------------------

Let us see this apparatus working for the type ``tuple``.
Here is a partial ``javap`` dump of ``PyTuple``
at the time of writing:

..  code-block:: none

    Compiled from "PyTuple.java"
    public class PyTuple extends AbstractList<Object>
            implements CraftedPyObject {
      public static final PyType TYPE;
      final Object[] value;
      public <E> PyTuple(E...);
      PyTuple(Collection<?>);
      PyTuple(Object[], int, int);
      static <E> PyTuple from(E[]);
      ...
      public PyType getType();
      private int __len__();
      private boolean __contains__(Object) throws Throwable;
      private Object __ne__(Object);
      private Object __eq__(Object);
      private Object __gt__(Object);
      private Object __ge__(Object);
      private Object __lt__(Object);
      private Object __le__(Object);
      private Object __add__(Object) throws Throwable;
      private Object __radd__(Object) throws Throwable;
      private Object __mul__(Object) throws Throwable;
      private Object __rmul__(Object) throws Throwable;
      private Object __getitem__(Object) throws Throwable;
      private int __hash__() throws Throwable;
      private Object __repr__();
      private Object __str__();
      ...
    }

(Package prefixes have been elided from this and similar listings.)

We recognise some special method names that ``tuple`` defines:
``__len__``, ``__add__``, ``__hash__``, etc..
We do not see definitions in ``PyTuple`` of the attribute access methods
``__getattribute__``, ``__setattr__`` and ``__delattr__``,
because ``tuple`` inherits them from ``object``:

..  code-block:: none

    Compiled from "PyBaseObject.java"
    public class PyBaseObject extends AbstractPyObject {
      public static final PyType TYPE;
      public PyBaseObject();
      ...
      static Object __repr__(Object);
      static Object __str__(Object);
      ...
      static Object __getattribute__(Object, String) throws Throwable;
      static void __setattr__(Object, String, Object) throws Throwable;
      static void __delattr__(Object, String) throws Throwable;
      public String toString();
      public PyType getType();
      ...
    }

This inheritance in Python does not require Java inheritance.
``PyTuple`` does not Java-inherit from ``PyBaseObject``.

Methods defined in ``tuple`` will be discovered by the type exposer,
and descriptors for them posted to ``tuple.__dict__``.
This includes slot wrapper descriptors
for the special methods of the data model defined by ``PyTuple``,
which contain a ``MethodHandle`` on the definition.

The methods are ``private`` instance methods
because we do not expect to use them directly from Java.
The methods could have been ``static``,
with a first argument of ``PyTuple self``,
but it is easier to write the implementations if they are instance methods.

..  code-block:: java

    public class PyTuple extends AbstractList<Object>
            implements CraftedPyObject {

        public static final PyType TYPE = PyType.fromSpec( //
                new Spec("tuple", MethodHandles.lookup()));
        // ...
        private int __len__() { return size(); }

        private boolean __contains__(Object o) throws Throwable {
            for (Object v : value) {
                if (Abstract.richCompareBool(v, o, Comparison.EQ)) {
                    return true;
                }
            }
            return false;
        }

        private Object __ne__(Object o) {
            return delegate.cmp(o, Comparison.NE);
        }
        // ...
    }

Towards the end of type construction,
type slots in the type object ``tuple`` are filled from
the ``MethodHandle`` in the matching descriptor along the MRO.
This is how ``tuple`` Python-inherits
``__getattribute__``, ``__setattr__`` and ``__delattr__`` etc.
from ``object``.


Special Methods in Python
=========================

During the definition of a class in Python,
the body of the class definition is executed
in a way similar to the execution of a function body.
This leaves behind a dictionary containing class members,
including the definition of methods,
and including special methods if any are defined.

Processing that dictionary creates the descriptors
that make the entries attributes accessible in the correct way.
In the case of special methods (defined in Python),
this includes placing a handle in the corresponding type slots,
able to call the function defined.

.. _Python Data Model:
    https://docs.python.org/3/reference/datamodel.html



``__new__`` and Java Constructors
=================================

..  note:: This is very VSJ 2 and needs updating.
    Unfortunately, at the time of writing,
    the proper approach to ``__new__`` still needs some thought,
    in the narrative with a settled answer here.

In CPython,
the ``tp_new`` slot of a particular instance of ``PyTypeObject``,
acts as the constructor for the type the ``PyTypeObject`` represents.
This section gives detailed consideration to the problem of
implementing its behaviour in Java.

A "second phase" of construction is performed by ``tp_init``,
but this has much the character of any other instance method.
Although called once automatically, it may be called again expressly,
if the programmer chooses.
``tp_new``, however, is a static method called once per object,
since creates a new instance each time.

Calling a type object
(that is, invoking the ``tp_call`` slot of the ``PyTypeObject`` for ``type``,
and passing it the particular ``PyTypeObject`` for ``C`` as the target)
is what normally leads to invoking the ``tp_new`` slot
on the ``PyTypeObject`` for ``C``,
and ``tp_init`` soon after.
An introduction to the topic,
by Eli Bendersky,
may be found in `Python object creation sequence`_.


Relation of ``tp_new`` to the Java constructor
==============================================

Close, but not close enough
---------------------------

It appears at first as if a satisfactory Java implementation
of the slot function would be the constructor in the defining class.
But a ``tp_new`` slot is inherited by copying,
and many Python types simply get theirs from ``object``.
The definition of ``tp_new`` executed in response to a call ``C()``
could easily be in some ancestor ``A`` on the MRO of ``C``.
The Java constructor for ``A`` would only be satisfactory if
the Java class implementing ``C`` were
the same as that implementing ``A``.
This will not be true in general.

An instance must be created somehow,
so a Java constructor must be invoked,
but from the observation above,
it isn't enough simply to place a ``MethodHandle`` to the constructor
in the ``tp_new`` slot,
even if the signature is made to match.


``__new__`` and a parallel
--------------------------

In cases where ``C`` customises ``tp_new`` in Python
(defines ``__new__``),
it is conventional for ``C.__new__`` to call ``super(C, cls).__new__``
before making its own customisations.
This use of ``super`` means the interpreter should
find ``__new__``, in the MRO of ``cls``, starting after ``C``,
and so the call is to the first ancestor of ``C`` defining it.
Something equivalent must happen in a built-in or extension type.

Since each ``__new__`` (or ``tp_new``) defers immediately to an ancestor,
the first customisation that *completes* is in the ``type`` of ``object``.
This is similar to the way in which Java constructors,
explicitly or implicitly,
first defer to their parent's constructor.
The ancestral line in Java traces itself all the way to ``Object``,
which is therefore the last constructor to start and first to complete.


Allocation before initialisation
--------------------------------

Recall that the first argument in each ``tp_new`` slot invocation
is the type of the target class ``C``.
The ``tp_new`` in the ``PyTypeObject`` for ``object`` in CPython
invokes a slot on the target class we haven't mentioned yet, ``tp_alloc``.
This allocates the right amount of memory for the target type ``C``,
in which the hierarchy of ``tp_new`` slot functions
will incrementally construct an instance of ``C`` from the arguments,
as they complete in reverse method-resolution order.

There is no parallel to the allocation step in Java source:
one cannot allocate an object separate from initialising it,
since an expression with the ``new`` keyword does both.
There *is* a JVM opcode (``new``)
that allocates an uninitialised object of the right size.
The source-level ``new`` generates this, and
an ``invokespecial`` for a target ``<init>()V`` method.
Allocation must happen in Java where object creation is initiated,
not in the ``tp_new`` of ``object`` as it can in CPython.


Examples guiding architectural choices
======================================

Example: extending a built-in
-----------------------------

Consider the following where we derive classes from ``list``
and then manipulate the ``__class__`` attribute of an instance.
What Java classes would make this possible?

..  code-block:: pycon

    >>> class MyList(list): pass
    ...
    >>> class MyList2(MyList): pass
    ...
    >>> m2 = MyList2()
    >>> m2.__class__ = MyList
    >>> m2.__class__ = list
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: __class__ assignment only supported for heap types or
        ModuleType subclasses

The very possibility of giving ``m2`` the Python class ``MyList``
tells us that both must be implemented by the same Java class,
since the Java class of an object cannot be altered.
However,
we were unable to give ``m2`` the type ``list`` (a ``PyList`` in Java).
This allows the implementation of ``MyList`` and ``MyList2`` to be
a distinct Java class from ``PyList``.

It had better be *derived* from ``PyList``
so we can apply its methods to instances of the sub-classes.
One thing we would have to add to this sub-class is a dictionary,
since instances of ``MyList`` have one.
Let's call this class ``PyListDerived`` here, as in Jython 2.
(In practice, an inner class of each built-in seems a tidy solution.)

In the following diagram,
the Python classes in our example are connected to
the Java classes that implement their instances.

..  uml::
    :caption: Extending a Python built-in

    skinparam class {
        BackgroundColor<<Python>> LightSkyBlue
        BorderColor<<Python>> Blue
    }

    object <<Python>>
    list <<Python>>
    MyList <<Python>>
    MyList2 <<Python>>

    MyList2 -|> MyList
    MyList -|> list
    list -|> object

    class PyListDerived {
        dict : PyDictionary
    }

    PyListDerived -|> PyList
    PyList -|> Object

    MyList2 .. PyListDerived
    MyList .. PyListDerived
    list .. PyList


Example: extending with ``__slots__``
-------------------------------------

Another possibility for sub-classing is
to specify a ``__slots__`` class attribute.
This suppresses the instance dictionary that was
automatic in the previous example.
Instances are not class re-assignable from other derived types.
Consider:

..  code-block:: pycon

    >>> class MyListXY(list):
    ...     __slots__ = ('x', 'y')
    ...
    >>> mxy = MyListXY()
    >>> mxy.__class__ = list
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: __class__ assignment only supported for heap types or
        ModuleType subclasses
    >>> mxy.__class__ = MyList
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: __class__ assignment: 'MyList' object layout differs from
        'MyListXY'
    >>> m2.__class__ = MyListXY
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: __class__ assignment: 'MyListXY' object layout differs from
        'MyList'

However,
they are class re-assignable from other derived classes,
provided the "layout" matches,
i.e. the slots have exactly the same names in order and number,
and there is (or isn't) an instance dictionary in both.

..  code-block:: pycon

    >>> class MyListXY2(list):
    ...     __slots__ = ('x', 'y')
    ...
    >>> mxy.__class__ = MyListXY2
    >>> class MyListAB(list):
    ...     __slots__ = ('a', 'b')
    ...
    >>> mxy.__class__ = MyListAB
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: __class__ assignment: 'MyListAB' object layout differs from
        'MyListXY2'

The possibility of giving ``mxy`` class ``MyListXY2``
tells us that both must be implemented by the same Java class.

In fact it is possible to derive again from a slotted class,
in such a way that it gains an instance dictionary,
or to add ``__slots__`` to a base class that has a dictionary.
(The purpose of ``__slots__`` in Python is
to save the space an instance dictionary occupies,
an advantage lost when the ideas are mixed,
but it must still work as expected.)
Instances of all these types may have their class re-assigned,
provided the constraint on ``__slots__`` is also met.

..  code-block:: pycon

    >>> class MyListMix(MyList2, MyListXY): pass
    ...
    >>> mix = MyListMix()
    >>> mix.a = 1
    >>> mix.__slots__
    ('x', 'y')

To support ``__slots__`` and instance dictionaries in these combinations,
we add a ``slots`` member to ``PyListDerived``.

..  uml::
    :caption: Extending a Python built-in (supporting ``__slots__``)

    skinparam class {
        BackgroundColor<<Python>> LightSkyBlue
        BorderColor<<Python>> Blue
    }

    object <<Python>>
    list <<Python>>
    MyList2 <<Python>>
    MyListXY <<Python>>
    MyListMix <<Python>>

    MyListMix -|> MyListXY
    MyListMix -|> MyList2
    MyList2 -|> list
    MyListXY -|> list
    list -|> object

    class PyListDerived {
        dict : PyDictionary
        slots : PyObject[]
    }

    PyListDerived -|> PyList
    PyList -|> ArrayList
    ArrayList -|> Object

    MyListMix .. PyListDerived
    MyListXY .. PyListDerived
    MyList2 .. PyListDerived
    list .. PyList

We have shown the slots implemented as an array,
which is the approach Jython 2 takes.
The dictionary of the type contains entries for "x" and "y",
that index the ``slots`` array in the instance.
Another possibility is to create a new type with fields "x" and "y",
but this requires careful book-keeping to ensure ``MyListXY2``
gets the same implementation class.


Example: extending with custom ``__new__``
------------------------------------------

Consider the case of a long inheritance chain (from ``list`` again),
including one class that customises ``__new__``:

..  code-block:: python

    class L1(list): pass

    class L2(L1):
        def __new__(c, *a, **k):
            obj = super(L2, c).__new__(c, *a, **k)
            obj.args = a
            return obj

    class L3(L2): pass

    x = L3("hello")

After running that script, we may examine what we created

..  code-block:: python

    >>> x
    ['h', 'e', 'l', 'l', 'o']
    >>> x.args
    ('hello',)

The definitions result in an MRO for ``L3`` of
``('L3', 'L2', 'L1', 'list', 'object')``.
The construction of ``x`` calls ``L2.__new__``.
Each class in the MRO gets its turn to customise the object.
We can illustrate how classes in Python are realised by objects in Java
in the following (somewhat abusive UML) diagram,
showing the Java ``PyType`` objects that implement
the Python classes in the discussion:

..  uml::
    :caption: Representing a Python MRO (including ``__new__``)

    skinparam class {
        BackgroundColor<<Python>> LightSkyBlue
        BorderColor<<Python>> Blue
    }

    object <<Python>>
    list <<Python>>
    L1 <<Python>>
    class L2 <<Python>> {
        {method} __new__(c, *a, **k)
    }
    L3 <<Python>>

    list -|> object
    L1 -|> list
    L2 -|> L1
    L3 -|> L2

    object "<u>:PyType</u>" as Tobject {
        name = "object"
    }

    object "<u>:PyType</u>" as Tlist {
        name = "list"
    }

    object "<u>:PyType</u>" as TL1 {
        name = "L1"
    }

    object "<u>:PyType</u>" as TL2 {
        name = "L2"
    }

    object "<u>:PyType</u>" as TL3 {
        name = "L3"
    }

    object "<u>:PyFunction</u>" as L2new {
        {field} __name__ = "__new__"
    }

    object "<u>:PyJavaFunction</u>" as listnew {
        {field} __name__ = "__new__"
    }


    TL3 -> TL2
    TL2 -> TL1
    TL1 -> Tlist
    Tlist -> Tobject

    L3 .. TL3
    L2 .. TL2
    L1 .. TL1
    list .. Tlist
    object .. Tobject

    TL2 -down-> L2new
    Tlist -down-> listnew

The functions in the diagram are (Python) attributes of the type objects,
implemented by descriptors in the dictionary of each type,
in this case under the key ``"__new__"``.
This complexity has been elided from the diagram.

During the building of the structure depicted,
the ``tp_new`` slot of ``L1`` is copied from that of ``list``,
the ``tp_new`` slot of ``L2`` is filled with a wrapper on ``L2.__new__``,
and the ``tp_new`` slot of ``L3`` is copied from that of ``L2``.
The pre-existing ``list.__new__`` is a wrapper invoking ``list.tp_new``.
It sounds as if the chain up to ``list`` is broken between ``L2`` and ``L1``,
and it would be if ``L2.__new__`` were not to call a super ``__new__``.

Now, consider constructing a new object of Python type ``L3``,
by calling ``L3()``.
We know that this invokes the slot ``tp_call`` on ``type``
with ``L3`` as target,
and that in turn invokes the ``tp_new`` slot on ``L3`` with ``L3`` as target.
The ``tp_new`` slot on ``L3`` is a copy of that in ``L2``
and so the code for ``L2.__new__`` is executed (with ``c = L3``).

The expression ``super(L2, c).__new__``
resolves to the ``__new__`` attribute of ``list``, by inheritance,
and this is a wrapper that invokes the method ``PyList.tp_new``.
Recall that the first argument to ``tp_new`` (a ``PyType``) must be
the type actually under construction, in this case ``L3``.

A conclusion about inheritance
------------------------------

We conclude from the examples that the behaviour of ``PyList.tp_new`` must be
to construct a plain ``PyList`` when the type argument is ``list``,
but a ``PyListDerived`` when it is a Python sub-class of ``list``.
``PyListDerived`` is a Java sub-class of ``PyList``
that potentially has ``dict`` and ``slots`` members.
Whether the object actually has ``dict`` or ``slots`` members (or both)
is deducible from the definition,
and must be available from the type object when we construct instances.


.. _Python object creation sequence:
    https://eli.thegreenplace.net/2012/04/16/python-object-creation-sequence


