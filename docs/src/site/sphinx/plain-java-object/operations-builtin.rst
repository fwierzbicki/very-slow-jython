..  plain-java-object/operations-builtin.rst


Operations on Built-in Types
############################

The pursuit of the plain Java objects idea requires a extensive change
to the preceding VSJ 2 (project ``rt2``) implementation,
although structurally VSJ 3 (project ``rt3``) will have much in common with it.

    Code fragments in this section are taken from
    ``rt3/src/main/java/.../vsj3/evo1``
    and corresponding test and other directories
    in the project source.

Direction of Travel
*******************

We will tackle first the question of how we find the ``MethodHandle``\s
that an interpreter would invoke or a ``CallSite`` bind as its target,
that is, we begin with a typical special method "slot".

In VSJ2 we shifted from seeing these slots as fundamental to Python,
as they seem at first in the source code CPython,
to regarding the data model as fundamental,
with its special methods exposed
through descriptors in the dictionary of the type.
We now see the type slots as a cache of that information,
attuned to the needs of a CPython interpreter.

In order to to get started on the "plain objects" idea,
we will begin with type construction using the exposure apparatus.
This involves copying and adapting a good portion of VSJ2
(taken at the ``evo4`` stage).
For the time being,
we defer actual interpretation of code in favour of unit tests
of the abstract object and other conjectured API.

VSJ2 also made a brief and inadequate pass over the question of building
call sites based on these cached values.
It was inadequate because it equated Java class to Python type
when generating guards, which we must now put right.

We'll do this first with a few familiar types,
and attempt to explain how we may define and access
a rich set of operations in the plain objects paradigm.



``PyType`` and ``Operations`` for ``float``
*******************************************

A Unary Operation ``float.__neg__``
===================================

In VSJ 2 we created the means to build a ``PyType``
for classes crafted to represent Python objects.
An example is ``PyFloat``,
representing the type ``float`` and its instances.
There the ``PyType`` provided a ``MethodHandle`` to each method of ``float``,
including the special methods (such as ``__neg__``)
for which it also acted as a cache.

Suppose we want to do the same again,
but now also to allow instances of ``java.lang.Double``
to represent instances of ``float``.
For each method, including the special methods,
we shall have to provide an implementation applicable to ``PyFloat``,
as before,
and also one applicable to ``Double``.

In fact, since the range and precision of ``Double``
are the same as those of ``PyFloat``,
we could manage without ``PyFloat`` entirely,
were it not that we need to define sub-classes of ``float`` in Python.
Sub-classes in Python must be represented by sub-classes in Java
(with a few exceptions)
and ``Double`` cannot be sub-classed.


Descriptive Structures
----------------------

Let's just address unary negative to begin with.
Suppose that in the course of executing a ``UNARY_NEGATIVE`` opcode,
the interpreter picks up an ``Object`` from the stack
and finds it to be a ``Double``.
How does it locate the particular implementation of ``__neg__``?

The structure we propose looks like this,
when realised for two integer values:

..  uml::
    :caption: Instance model of ``float`` and its operations

    object "1e42 : PyFloat" as x
    object "PyFloat : Class" as PyFloat.class

    object " : MethodHandle" as xneg {
        target = PyFloatMethods.__neg__(PyFloat)
    }

    object "float : PyType" as floatType

    x --> PyFloat.class
    PyFloat.class --> floatType : ops
    floatType --> floatType
    floatType --> xneg : op_neg

    object "42.0 : Double" as y
    object "Double : Class" as Double.class
    object " : Operations" as yOps

    object " : MethodHandle" as yneg {
        target = PyFloatMethods.__neg__(Double)
    }

    y --> Double.class
    Double.class --> yOps : ops
    yOps -left-> floatType : type
    yOps --> yneg : op_neg

    object " : Map" as dict
    object " : PyWrapperDescr" as neg {
        name = "__neg__"
    }

    floatType --> dict : dict
    dict --> neg
    neg --> xneg
    neg --> yneg


We separate the responsibilities of ``PyType``,
where they have to adapt to the specific Java implementation,
into:

* an ``Operations`` object specialised to one Java implementation class, and
* the ``PyType`` containing the information common to all implementations.

A ``PyType`` is a particular kind of ``Operations`` object,
describing the *canonical implementation* (``PyFloat`` in this case).
The ``Operations`` object for an alternative *adopted implementation*,
is not a ``PyType``.

There can only be one descriptor for ``float.__neg__``.
How does it describe the several implementations?
Clearly the descriptor must reference all the implementations of its method,
and during execution we must choose one
that matches the class of object appearing as ``self``.

As before, we shall have a caching scheme,
in which a slot on each ``Operations`` object (including ``PyType``),
holds the handle for its particular Java class.
In the present case, that cache will be the ``op_neg`` slot.


Method Implementations
----------------------

Methods defined in Java are exposed as Python methods
thanks to the class ``Exposer``.
At the time of writing,
the design provides for multiple styles of definition
of the special methods:
as instance methods or or as static methods in the canonical class,
and as static methods in an auxiliary class.

This last option is the one we use predominantly for types like ``float``,
that have multiple implementing classes.
We are able to choose the style method-by-method, with some constraints.
The operations on ``Double`` have to be ``static`` methods:
we can't very well open up ``java.lang.Double`` and add them there!

When we come to study the implementation of ``int``,
we shall find that the list of types that can appear as ``self``
can be longer than the set of adopted implementations.
We shall use the term *accepted* implementations for the full list.

In the style we apply to ``__neg__`` and many other ``float`` methods,
we create a new class in which ``static`` methods
define the operations for the canonical and all accepted implementations.
We could reasonably think of the canonical implementation as
the *first accepted* implementation (implementation zero).

The defining implementation class will specify, during initialisation,
the Java classes that are the adopted and accepted implementations,
and the name of this extra class defining the methods.
The defining class now begins something like this:

..  code-block:: java

    public class PyFloat extends AbstractPyObject {

        static final PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("float", MethodHandles.lookup())
                        .adopt(Double.class)
                        .methods(PyFloatMethods.class));

It suits us still to define some methods by hand in ``PyFloat``,
but the class containing (most of) the methods is ``PyFloatMethods``.
It is generated by a script, as it is somewhat repetitious:

..  code-block:: java

    class PyFloatMethods {
        // ...
        static Object __abs__(PyFloat self) { return Math.abs(self.value); }
        static Object __abs__(Double self)
                { return Math.abs(self.doubleValue()); }
        static Object __neg__(PyFloat self) { return -self.value; }
        static Object __neg__(Double self) { return -self.doubleValue(); }


Forming a ``PyWrapperDescr`` for ``__neg__``
--------------------------------------------

The ``Exposer`` runs over the defining class ``PyFloat``
and the "method class" ``PyFloatMethods``.
It builds a table of entries (``WrapperDef``)
that collect ``Method``\s of each special method name (``__neg__``).

It then turns each collection into a ``PyWrapperDescr``.
The ``PyWrapperDescr`` contains a table of handles,
one for each accepted Java class implementing ``float``,
in the order those classes are catalogued by the defining type.

..  code-block:: java

    abstract class PyWrapperDescr extends Descriptor {

        static final PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("wrapper_descriptor",
                        MethodHandles.lookup()).flagNot(Flag.BASETYPE));
        final Slot slot;
        final MethodHandle[] wrapped;

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

        // ...

The ``Exposer`` method that does this is run
when the ``PyType`` for ``float`` builds its dictionary.
This places the ``PyWrapperDescr`` in the dictionary for ``float``.
(Other processes will add to this dictionary,
including, in the general case, methods defined in Python.)


Slots in the ``Operations`` object
----------------------------------

Once the dictionary is complete in the ``PyType`` for ``float``,
``PyType`` will build an ``Operations`` object
for the adopted type ``Double``.
It will fill the ``op_neg`` slot in that object
from the corresponding ``PyWrapperDescr.wrapped[1]`` entry just discussed,
because ``Double`` is at index ``1``
in the accepted implementations of ``float``'.
A ``PyType`` is itself an ``Operations`` object,
for the canonical implementation,
and so it fill its own ``op_neg`` from ``PyWrapperDescr.wrapped[0]``.

Slots inherited from an ancestor (here only ``object``)
will be filled the same way.
It is important to recognise that
it is the index of a Java class in the Python type that *defined* the method
that allows us to locate the correct wrapped handle.

A ``PyWrapperDescr`` is a callable object,
in which ``__call__`` is implemented as:

..  code-block:: java

    abstract class PyWrapperDescr extends Descriptor {
        protected Object __call__(PyTuple args, PyDict kwargs)
                throws TypeError, Throwable {

            int argc = args.value.length;
            if (argc > 0) {
                // Split the leading element self from args
                Object self = args.value[0];
                if (argc == 1) {
                    args = PyTuple.EMPTY;
                } else {
                    args = new PyTuple(args.value, 1, argc - 1);
                }

                // Work out how to call this descriptor on that object
                Class<?> selfClass = self.getClass();
                int index = objclass.indexAccepted(selfClass);

                // Make sure that the first argument is acceptable as 'self'
                PyType selfType = PyType.of(self);
                if (index < 0 || !Abstract.recursiveIsSubclass(selfType,
                        objclass)) {
                    throw new TypeError(DESCRIPTOR_REQUIRES, name,
                            objclass.name, selfType.name);
                }

                return callWrapped(self, index, args, kwargs);

            } else {
                throw new TypeError(DESCRIPTOR_NEEDS_ARGUMENT, name,
                        objclass.name);
            }
        }

``PyWrapperDescr`` is sub-classed multiple ways,
overriding ``callWrapped``,
according to the signature of the special method it represents.


In fact, when seeking the index of an accepted implementation class,
amongst the implementations available,
we search not for the exact ``self`` class in the accepted implementations,
but for any class *assignable* from the class at hand.
``object`` has only one implementation class ``Object``,
which is assignable from any type.
This satisfies the requirement that its methods be applicable to any object.

The ``PyType`` for ``float`` will then register these ``Operations`` objects
so that they may be found by ``ClassValue`` lookup,
whenever either a ``Double`` or a ``PyFloat`` is encountered.


An Implication for Bootstrapping
--------------------------------

Note that if a Java class were to be encountered by the run-time
before its canonical counterpart could register it,
it would be treated as a "found" Java class,
and this would prevent it becoming an adopted implementation as intended.
Types with adopted implementations must initialise before this can happen,
and so we make them bootstrap types.
A list read from configuration is imaginable
as an alternative to hard-coding,
but it would have to be acted on early in the life of the type system.


Abstract API ``negative()``
---------------------------

One purpose we have for the ``op_neg`` slot is
in the abstract API method ``Object negative(Object)``,
which in turn supports the interpreter for CPython byte code.

..  code-block:: java

        public static Object negative(Object v) throws Throwable {
            try {
                return Operations.of(v).op_neg.invokeExact(v);
            } catch (Slot.EmptyException e) {
                throw operandError(Slot.op_neg, v);
            }
        }

The difference from previous versions is only that,
rather than finding the type of ``v``,
and getting the ``op_neg`` slot from it,
we ask for its ``Operations`` object.
Behind ``Operations.of(v)`` is the ``ClassValue`` lookup ,
that retrieves the ``Operations`` object
that ``PyType`` registered for ``v.getClass()``.
If ``v`` is a ``PyFloat`` sub-class
that result will be the ``PyType`` of ``float``,
but it is much more likely that ``v`` should be a ``Double``.

We are using the same convention as before
to place the detection of an empty slot outside the main flow of control.
Empty slots hold a handle to a method that throws ``EmptyException``.


Constructing a Unary ``CallSite``
---------------------------------

The second purpose of these acrobatics,
and the one that makes the complexity worthwhile (we hope),
is the creation of efficient call sites in compiled Python code.
We now sketch how we do so in the unary case.
A lot of supporting code has been elided:

..  code-block:: java

    public class PyRT {
        // ...
        static class UnaryOpCallSite extends MutableCallSite {
            // ...
            private final Slot op;

            public UnaryOpCallSite(Slot op)
                    throws NoSuchMethodException, IllegalAccessException {
                super(UOP);
                this.op = op;
                setTarget(fallbackMH.bindTo(this));
            }

            @SuppressWarnings("unused")
            private Object fallback(Object v) throws Throwable {
                fallbackCalls += 1;
                Operations vOps = Operations.of(v);
                MethodHandle resultMH, targetMH;
                if (op.isDefinedFor(vOps)) {
                    resultMH = op.getSlot(vOps);
                } else {
                    // Not defined for this type, so will throw
                    resultMH = op.getOperandError();
                }

                Object result = resultMH.invokeExact(v);

                // MH for guarded invocation (becomes new target)
                MethodHandle guardMH = CLASS_GUARD.bindTo(v.getClass());
                targetMH = guardWithTest(guardMH, resultMH, getTarget());
                setTarget(targetMH);

                return result;
            }
            // ...
        }

The interesting part is the method ``fallback``.
This has the same signature as the site,
once bound to the call site as the target instance,
and is the first installed target of the site.

``fallback`` will compute the result of the call for a particular argument,
which it does by getting the method handle cached in the ``Operations``
for the class of the argument.
Before it returns the result of invoking that handle,
it makes it the target of the call site,
guarded by a test for the particular argument's class,
with the existing handle (``fallback``) as the alternative.
In this way, the site is able to make the same invocation call efficiently,
next time the same Java class is encountered.

As the site is invoked for different Java classes,
which could be any class,
not just the adopted implementations of a single type,
it will build a chain of guarded invocations,
equivalent to a chain of ``if (v instanceof C) { ... } else ...`` clauses
each guarding the proper implementation of the unary operation.

The chain could grow long,
although it will only contain the types actually encountered
in a particular location.
It is also a structure the JVM is able to inspect and optimise.

We do not bind ``resultMH`` as the new target if
invoking it throws an exception.
This is intentional, as it does not seem worth optimising for those cases.

We do not have a compiler yet to generate code using ``invokedynamic``
in order to exercise this properly.
However, we can invoke it as a test like this:

..  code-block:: java

    class FloatCallSites {

        /** Test invocation of __neg__ call site on accepted classes. */
        @Test
        void site_neg() throws Throwable {

            Object dx = Double.valueOf(42.0);
            Object px = new PyFloat(42.0);

            // Bootstrap the call site
            UnaryOpCallSite cs = new UnaryOpCallSite(Slot.op_neg);
            MethodHandle invoker = cs.dynamicInvoker();

            // Update and invoke for PyFloat, Double
            for (Object x : List.of(px, dx)) {
                final Object res = invoker.invokeExact(x);
                assertPythonType(PyFloat.TYPE, res);
                assertEquals(-42.0, PyFloat.asDouble(res));
            }
        }
    }

This works for unary operations on ``float``.
Whether this is correct yet for all styles of object implementation
remains to be seen.


A Binary Operation ``float.__sub__``
====================================

There was a one-to-one relationship between ``negative()``
and the ``op_neg`` slot or ``float.__neg__``.
When it comes to binary operations,
it is a little more complicated:
``subtract()`` depends on ``op_sub`` and ``op_rsub``,
so we cannot consider ``__sub__`` without considering ``__rsub__`` too.
We need both to implement subtraction with Python semantics.


Implementing ``__sub__`` and ``__rsub__``
-----------------------------------------

Binary operations conform to the pattern ``op(self, other)``.
As in the implementation we developed for ``__neg__``,
we provide an entry point specific to
each accepted implementation of ``self``.

It is simple to allow for additional arguments.
In a binary operation there is just one,
which must have type ``Object`` since the interpreter will simply pass
whatever is on the stack when it comes to the ``BINARY_SUBTRACT``.
Other signatures,
for example ``__call__(MyType, PyTuple, PyDict)``,
will still offer some type safety beyond the ``self`` argument.

We generate one method special method implementation for each accepted type,
in same class as before:

..  code-block:: java

    class PyFloatMethods {
        // ...
        static Object __sub__(PyFloat v, Object w) {
            try { return v.value - convert(w); }
            catch (NoConversion e) { return Py.NotImplemented; }
        }
        static Object __sub__(Double v, Object w) {
            try { return v.doubleValue() - convert(w); }
            catch (NoConversion e) { return Py.NotImplemented; }
        }
        static Object __rsub__(PyFloat w, Object v) {
            try { return convert(v) - w.value; }
            catch (NoConversion e) { return Py.NotImplemented; }
        }
        static Object __rsub__(Double w, Object v) {
            try { return convert(v) - w.doubleValue(); }
            catch (NoConversion e) { return Py.NotImplemented; }
        }
        // ...
        private static double convert(Object v)
                throws NoConversion, OverflowError {
            if (v instanceof Double)
                return ((Double) v).doubleValue();
            else if (v instanceof PyFloat)
                return ((PyFloat) v).value;
            else
                // BigInteger, PyLong, Boolean, etc.
                // or throw PyObjectUtil.NO_CONVERSION;
                return PyLong.convertToDouble(v);
        }
    }

The calculation is carried out in a common currency,
the Java primitive ``double``.
We let the compiler box the result, always a ``Double``.

Although we are able to land on an implementation
strongly-typed for the ``self`` argument and go directly to ``double``,
we have to resort to a rat's nest of ``if``\-statements
to convert the ``other`` argument based on a discovered type.
This nest of ``if``\s continues in ``PyLong.convertToDouble()``.
CPython ``floatobject.c`` has analagous code, except we avoid
processing both arguments through the nest.

If we cannot perform the conversion, according to the Python data model,
the special method must return ``NotImplemented``.
We throw a special exception ``NoConversion``,
which the special method must catch and convert,
as a succinct way to make this happen.

We form ``PyWrapperDescr``\s for ``__sub__`` and ``__rsub__``
by the process already described.
Each has an array ``MethodHandle[] wrapper``
containing handles for the methods specialised on ``self``.
The handles populate slots ``op_sub`` and ``op_rsub``
in the ``Operations`` objects, in the way familiar from ``__neg__``.


Abstract API ``subtract()``
---------------------------

The two slots support subtract roughly as in VSJ 2,
except it got a little more complicated in VSJ 3.

All the binary operations converge on one implementation ``binary_op``.
Access to the method handles is via
the ``Operations`` object of each operand,
while the decision on the order to consult them for implementations
depends on the ``PyType`` (equality or sub-classing),
which we have to get in a separate step.
In VSJ 2 (As in CPython) the type object serves both purposes.

..  code-block:: java

        public static Object subtract(Object v, Object w) throws Throwable {
            return binary_op(v, w, Slot.op_sub);
        }

        private static Object binary_op(Object v, Object w, Slot binop)
                throws TypeError, Throwable {
            try {
                Object r = binary_op1(v, w, binop);
                if (r != Py.NotImplemented) { return r; }
            } catch (Slot.EmptyException e) {}
            throw operandError(binop, v, w);
        }

        private static Object binary_op1(Object v, Object w, Slot binop)
                throws Slot.EmptyException, Throwable {

            Operations vOps = Operations.of(v);
            PyType vtype = vOps.type(v);

            Operations wOps = Operations.of(w);
            PyType wtype = wOps.type(w);

            MethodHandle slotv, slotw;

            if (wtype == vtype) {
                // Same types so only try the binop slot
                slotv = binop.getSlot(vOps);
                return slotv.invokeExact(v, w);

            } else if (!wtype.isSubTypeOf(vtype)) {
                // Ask left (if not empty) then right.
                slotv = binop.getSlot(vOps);
                if (slotv != BINARY_EMPTY) {
                    Object r = slotv.invokeExact(v, w);
                    if (r != Py.NotImplemented) { return r; }
                }
                slotw = binop.getAltSlot(wOps);
                return slotw.invokeExact(w, v);

            } else {
                // Right is sub-class: ask first (if not empty).
                slotw = binop.getAltSlot(wOps);
                if (slotw != BINARY_EMPTY) {
                    Object r = slotw.invokeExact(w, v);
                    if (r != Py.NotImplemented) { return r; }
                }
                slotv = binop.getSlot(vOps);
                return slotv.invokeExact(v, w);
            }
        }

Despite the complexity, performance is not bad
(see the benchmarks for :ref:`benchmark-binary-vsj3`).
When both types are built-in, and cannot change their behaviour,
we can do much better.


Binary Class-Specific Methods
-----------------------------

The general implementation we have shown is correct,
and this generality is necessary to support:

* methods in sub-classes in Python, and
* invocation via the descriptor, e.g. ``float.__sub__(51.0, 9)``

In the unary call site for ``op_neg``
we showed how the site would specialise itself
to the classes actually received as ``self``.
This was possible because the handle we invoke is determined
from the type (hence from the Java class), and this choice could be cached.
In a binary site,
we should like to specialise to the *pair* of classes received.

Notice how frequently, in the general binary case,
we anticipate an empty slot or test for a ``NotImplemented`` on return.
But these occurrences are, in the case of many built-in types,
also strictly determined by the types involved.
And so also is the handle that ultimately succeeds in computing the result.

We will eliminate these tests by defining a method
for each acceptable implementation of the type,
and each (second) operand that may be combined with it.
If there is no such combination,
we will act as if we knew in advance it would return ``NotImplemented``.
Where one of the types does not participate in this scheme,
we may still have to combine handles under a test for ``NotImplemented``.

When defining these class-specific methods ``op(v, w)``,
only accepted implementation classes need be supported as ``v``,
but we could receive anything as ``w``.
For ``float`` in particular,
we will have to allow as operands the accepted implementations of ``int``,
including its sub-class ``bool`` and sub-classes defined in Python.
However, all other second argument types are ``NotImplemented``.

..  code-block:: java

    class PyFloatBinops {
        // ...
        static Object __sub__(PyFloat v, PyFloat w) {
            return v.value - w.value; }
        static Object __sub__(PyFloat v, Double w) {
            return v.value - w.doubleValue(); }
        static Object __sub__(PyFloat v, Integer w) {
            return v.value - w.doubleValue(); }
        static Object __sub__(PyFloat v, BigInteger w) {
            return v.value - PyLong.convertToDouble(w); }
        static Object __sub__(PyFloat v, PyLong w) {
            return v.value - PyLong.convertToDouble(w.value); }
        static Object __sub__(PyFloat v, Boolean w) {
            return v.value - (w.booleanValue() ? 1.0 : 0.0); }
        static Object __sub__(Double v, PyFloat w) {
            return v.doubleValue() - w.value; }
        // ... and so on, and __rsub__, and other binary operations

We may think of the methods as forming as a grid:
a row for each accepted implementation class, and
a column for each accepted or supported operand class.

In the defining class ``PyFloat``,
we signal through the ``PyType.Spec`` that we build there,
the additional operand types we support beyond the accepted implementations,
and the class defining the class-specific binary operations:

..  code-block:: java
    :emphasize-lines: 6-7, 9

    public class PyFloat extends AbstractPyObject {

        static final PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("float", MethodHandles.lookup())
                        .adopt(Double.class)
                        .operand(Integer.class, BigInteger.class,
                                PyLong.class, Boolean.class)
                        .methods(PyFloatMethods.class)
                        .binops(PyFloatBinops.class));


When ``PyType`` processes the specification,
it will have the ``Exposer`` read this class too,
and look for the binary operations of all combinations of class.
it will build a table (a ``BinopGrid``) for each method supported this way.
(We can decide method-by-method to make this enumeration or not,
but any grid has to be completely filled if it exists at all.)
Each such grid is entered in a dictionary *on the type*,
and is used when constructing the call site.


Constructing a Binary ``CallSite``
----------------------------------

The way we use the grid of methods in a call site
reflects the structure of the general implementation of binary operations,
except that we separate deciding what to do (based on types)
from doing it (embedded in the handle).

As in the unary case,
all the interesting logic is in the fallback method,
but even that is quite complicated,
so we will show only what happens when both types are equal.

..  code-block:: java

    static class BinaryOpCallSite extends MutableCallSite {
        // ...
        /**
         * @param v left operand
         * @param w right operand
         * @return {@code op(v, w)}
         * @throws Throwable on errors or if not implemented
         */
        @SuppressWarnings("unused")
        private Object fallback(Object v, Object w) throws Throwable {
            fallbackCalls += 1;
            Operations vOps = Operations.of(v);
            PyType vType = vOps.type(v);
            Operations wOps = Operations.of(w);
            PyType wType = wOps.type(w);
            MethodHandle resultMH, targetMH;

            if (wType == vType) {
                // Same types so only try the op slot
                resultMH = singleType(vType, vOps, wOps);
            } else if (!wType.isSubTypeOf(vType)) {
                // Ask left (if not empty) then right.
                resultMH = leftDominant(vType, vOps, wType, wOps);
            } else {
                // Right is sub-class: ask first (if not empty).
                resultMH = rightDominant(vType, vOps, wType, wOps);
            }

            Object result = resultMH.invokeExact(v, w);

            // MH for guarded invocation (becomes new target)
            MethodHandle guardMH = insertArguments(CLASS2_GUARD, 0,
                    v.getClass(), w.getClass());
            targetMH = guardWithTest(guardMH, resultMH, getTarget());
            setTarget(targetMH);

            return result;
        }

        private MethodHandle singleType(PyType type, Operations vOps,
                Operations wOps) {

            MethodHandle slotv;

            // Does the type define class-specific implementations?
            Operations.BinopGrid binops = type.binopTable.get(op);
            if (binops != null) {
                // Are the classes of v, w supported as operands?
                slotv = binops.get(vOps, wOps);
                if (slotv != BINARY_EMPTY) { return slotv; }
            } else {
                // The type provides no class-specific implementation,
                slotv = op.getSlot(vOps);
            }

            if (slotv == BINARY_EMPTY) {
                // Not defined for this type, so will throw
                return op.getOperandError();
            } else {
                // slotv is a handle that may return Py.NotImplemented,
                return firstImplementer(slotv, op.getOperandError());
            }
        }

If the ``BinopGrid`` exists on the type for the slot,
and co-ordinates supplied by the classes of operand provided are valid,
we immediately have a handle that will compute the result.
It must do so without throwing ``EmptySlot`` or returning ``NotImplemented``.

We test the return from the look-up against ``BINARY_EMPTY``,
not because the grid would ever contain that,
but because that is how the lookup indicates that the classes did not
match accepted and operand types, respectively, for the Python type.
In the equal type case, that shouldn't happen at all,
but it can happen at the corresponding places
in the left and right-dominant cases where the types differ.

Benchmarks show that the the method handles returned from this logic
are successfully inlined by the JVM. (See :ref:`benchmark-binary-vsj3-indy`.)



``PyType`` and ``Operations`` for ``int`` and ``bool``
******************************************************

Let us repeat the exercise for ``int`` and ``bool``,
as there are some complications worth examining.
These arise from:

* the fact that, in Python, ``bool`` is a sub-class of ``int``, and
* because we would like to identify Python ``True`` and ``False``
  with Java ``Boolean.TRUE`` and ``Boolean.FALSE``.

``bool`` inherits methods from ``int``,
which is to say that ``__neg__``, for example,
looked up on ``bool`` is found on ``int``.

..  code-block:: python

    >>> bool.__neg__ is int.__neg__
    True

Our implementation of that method and others in ``int``
must offer an implementation that can take a ``bool``
(a Java ``Boolean``)
as the ``self`` argument.
These are reasonable (and the same thing) in Python:

..  code-block:: python

    >>> int.__neg__(True)
    -1
    >>> -True
    -1


The Unary Operation ``int.__neg__``
===================================

``PyLong`` is the canonical implementation of ``int``.
We also allow instances of ``Integer`` and ``BigInteger``
to represent instances of ``int``.
At first it seems that
all we need do is reproduce the pattern we used for ``float``,
with these three accepted implementations instead of the two in ``float``.
For each method, including the special methods,
we would have to provide implementations applicable to
``Integer``, ``BigInteger`` and ``PyLong``.

For ``__neg__`` we should expect to see
a signature for the canonical and each adopted implementation:

..  code-block:: java

    class PyLongMethods {
        // ...
        static Object __neg__(PyLong self) { return self.value.negate(); }
        static Object __neg__(Integer self) { return -self.intValue(); }
        static Object __neg__(BigInteger self) { return self.negate(); }


Java ``Boolean`` is not a sub-class of any adopted implementation,
and so none of these signatures for ``__neg__`` is applicable to it.
We are missing:

..  code-block:: java

    class PyLongMethods {
        // ...
        static Object __neg__(Boolean self) { return self ? -1 : 0; }


Adopting ``Boolean``
--------------------

The structure we propose to deal with this is as follows,
when realised for two kinds of integer `1` and boolean ``True``.
Although this involves a lot of objects,
it is very regular in structure.

..  uml::
    :caption: Instance model of ``int`` and ``bool`` finding ``op_neg``

    object "1 : PyLong" as x
    object "PyLong : Class" as PyLong.class

    object " : MethodHandle" as xneg {
        target = PyLongMethods.__neg__(PyLong)
    }

    object "int : PyType" as intType {
    }
    object " : Map" as intDict
    intType --> intDict : dict

    x --> PyLong.class
    PyLong.class --> intType : ops
    intType --> xneg : op_neg

    object "1 : Integer" as y
    object "Integer : Class" as Integer.class
    object " : Operations" as yOps

    object " : MethodHandle" as yneg {
        target = PyLongMethods.__neg__(Integer)
    }

    y --> Integer.class
    Integer.class --> yOps : ops
    yOps -right-> intType : type
    yOps --> yneg : op_neg

    object "bool : PyType" as boolType
    object " : Map" as boolDict
    boolType --> boolDict : dict

    object "True : Boolean" as z
    object "Boolean : Class" as Boolean.class
    object " : Operations" as zOps

    object " : MethodHandle" as zneg {
        target = PyLongMethods.__neg__(Boolean)
    }

    z --> Boolean.class
    Boolean.class --> zOps : ops
    zOps -left-> boolType : type
    zOps ---> zneg : op_neg

    object " : PyWrapperDescr" as neg {
        name = "__neg__"
    }

    intDict --> neg
    neg --> xneg
    neg --> yneg
    neg --> zneg
    intType <-left- boolType : base
    boolType ...> neg : lookup("~__neg__")


The point to note is that the same ``PyWrapperDescr`` object
is found by look-up on both ``int`` and ``bool``:
on ``int`` because it is defined there,
and on ``bool`` by inheritance along the MRO.
Each ``Operations`` object caches the correct handle for its Java class,
because the defining type object indicates
which handle within the ``PyWrapperDescr`` to take.

The definition of ``PyLong`` begins like this:

..  code-block:: java

    class PyLong implements CraftedType {

        static PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("int", MethodHandles.lookup())
                        .adopt(BigInteger.class, Integer.class)
                        .accept(Boolean.class) //
                        .methods(PyLongMethods.class)
                        .binops(PyLongBinops.class));

Note that ``Boolean`` is *accepted* as a ``self`` argument in ``PyLong``,
but it is not *adopted*,
since we do not want a ``Boolean`` to be recognised as an ``int``
by ``PyType.of()``.
We also make Boolean an accepted implementation
in the Python script that generates ``PyLongMethods.java``
and ``PyLongBinops.java``,
to obtain the special methods and class-specific implementations
that would otherwise be missing.

``Boolean`` is *adopted* in ``PyBool``,
so it will be recognised as a ``bool`` by ``PyType.of()``.
It is also the canonical and only accepted implementation:
there are no instances  of ``PyBool`` or sub-classes of ``bool``.
The definition of ``PyBool`` begins like this:

..  code-block:: java

    final class PyBool {

        static final PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("bool", MethodHandles.lookup())
                        .canonical(Boolean.class) //
                        .base(PyLong.TYPE) //
                        .flagNot(PyType.Flag.BASETYPE));

All the methods of ``bool`` unique to it are defined in ``PyBool``,
as they are few and we do not need to generate them by a script.

It is somewhat unsatisfying that
``PyLong`` should have to know about ``PyBool``
when the latter is a sub-class.
It would be complicated to avoid this by a general-purpose method,
perhaps re-working the definition of a built-in type
once a built-in sub-type is encountered and not covered by adopted types,
and we may have here the only case where it would be used.


Some Dots on the RADAR
======================

Here is a list of design problems looming already.
Some of these were already apparent for VSJ 2 at ``evo4``,
but since we can see them now,
we may design VSJ 3 with them in mind.

*   The type of attribute names were strongly typed to ``PyUnicode``
    in the VSJ 2 API to ``__getattribute__``, ``__setattr__``, etc.,
    obviating checks in the code.
    If we allow (prefer, even) ``String`` as ``str``,
    we still need ``PyUnicode`` as the canonical type
    (for above BMP strings and Python sub-classes of ``str``).
    So attribute access must now accept ``Object`` (at some level).
    We'd like this to be efficient in call sites
    where a Java ``String`` (UTF-16) may be guaranteed.
    Possibly make this the slot signature.
*   The keys of dictionaries must compare using ``__eq__`` and ``__hash__``
    even when the key is a plain Java object.
    We may not directly use a Java ``Map`` implementation
    directly as the Python implementation,
    except we wrap it in an object defining comparison and hashing.
    (This problem may be latent in VSJ 2.)
    We may avoid this for type dictionaries
    exploiting the string nature of attributes.
*   For types defined in Python,
    the Java class does not define the type,
    so the ``Operations`` slots will indirect
    via a type written on the instance,
    either to another ``Operations`` object specific to the type,
    or directly to the descriptor.
*   Descriptors that appear in the dictionary of the type
    must somehow be applicable to all implementations of that type,
    and therefore contain multiple handles,
    indexed by the implementation (by ``Operations``).
    In ``int.__neg__(42)`` we must invoke index 1.
    Descriptors are inherited by Python sub-classes to which these
    adopted implementations are largely irrelevant,
    since a sub-class Java extends only the canonical implementation.
*   When a type implemented in Java,
    and possessing adopted alternative implementations,
    inherits a ``PyWrapperDescr``,
    the method handle array ``wrapped`` in the inherited descriptor
    has entries that match the implementation types of the inherited class,
    and these may not match the accepted implementations of the receiver.
    How may the inherited special method be applied
    to a sub-class instance that of adopted class?
    This happens all the time for the special case of ``object``,
    where all implementations trivially extend ``Object``.
    Other examples are rare.
    If ``Boolean`` is an acceptable implementation of ``bool``,
    inherited ``int`` operations must be applicable to it.
    Should we conclude that each accep[table implementations of a sub-class
    must be a subclass of some implementation of it Python super-type?
