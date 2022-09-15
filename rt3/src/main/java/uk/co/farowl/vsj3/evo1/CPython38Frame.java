// Copyright (c)2022 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import uk.co.farowl.vsj3.evo1.base.InterpreterError;

/** A {@link PyFrame} for executing CPython 3.8 byte code. */
class CPython38Frame extends PyFrame<CPython38Code> {

    /*
     * Translation note: NB: in a CPython frame all local storage
     * local:cell:free:valuestack is one array into which pointers are
     * set during frame construction. For CPython byte code in Java,
     * three arrays seems to suit.
     */

    /**
     * The concatenation of the cell and free variables (in that order).
     * We place these in a single array, and use the slightly confusing
     * CPython name, to maximise similarity with the CPython code for
     * opcodes LOAD_DEREF, STORE_DEREF, etc..
     * <p>
     * Non-local variables used in the current scope <b>and</b> a nested
     * scope are named in {@link PyCode#cellvars}. These come first.
     * <p>
     * Non-local variables used in the current scope or a nested scope,
     * <b>and</b> in an enclosing scope are named in
     * {@link PyCode#freevars}. During a call, these are provided in the
     * closure, copied to the end of this array.
     */
    final PyCell[] freevars;
    /** Simple local variables, named in {@link PyCode#varnames}. */
    final Object[] fastlocals;
    /** Value stack. */
    final Object[] valuestack;

    /** Index of first empty space on the value stack. */
    int stacktop = 0;

    /** Assigned eventually by return statement (or stays None). */
    Object returnValue = Py.None;

    /**
     * Create a {@code CPythonFrame}, which is a {@code PyFrame} with
     * the storage and mechanism to execute a module or isolated code
     * object (compiled to a {@link CPython38Code}.
     *
     * The caller specifies the local variables dictionary explicitly:
     * it may be the same as the {@code globals}.
     *
     * @param code that this frame executes
     * @param interpreter providing the module context
     * @param globals global name space
     * @param locals local name space
     */
    CPython38Frame(Interpreter interpreter, CPython38Code code,
            PyDict globals, Object locals) {
        super(interpreter, code, globals, locals);
        valuestack = new Object[code.stacksize];
        freevars = EMPTY_CELL_ARRAY;

        // The need for a dictionary of locals depends on the code
        EnumSet<PyCode.Trait> traits = code.traits;
        if (traits.contains(PyCode.Trait.NEWLOCALS)
                && traits.contains(PyCode.Trait.OPTIMIZED)) {
            fastlocals = new Object[code.nlocals];
        } else {
            fastlocals = null;
        }
    }

    @Override
    Object eval() {

        // Evaluation stack and index
        final Object[] s = valuestack;
        int sp = this.stacktop;

        // Cached references from code
        final String[] names = code.names;
        final Object[] consts = code.consts;
        final char[] wordcode = code.wordcode;
        final int END = wordcode.length;

        /*
         * Opcode argument (where needed). See also case EXTENDED_ARG.
         * Every opcode that consumes oparg must set it to zero.
         */
        int oparg = 0;

        // Local variables used repeatedly in the loop
        Object v, w;
        int n, m;
        String name;
        PyTuple.Builder tpl;
        PyList lst;

        loop: for (int ip = 0; ip < END; ip++) {
            /*
             * Pick up the next instruction. Because we use a word
             * array, our ip is half the CPython ip. The latter, and all
             * jump arguments, are always even.
             */
            int opword = wordcode[ip];

            // Comparison with CPython macros in c.eval:
            // TOP() : s[sp-1]
            // PEEK(n) : s[sp-n]
            // POP() : s[--sp]
            // PUSH(v) : s[sp++] = v
            // SET_TOP(v) : s[sp-1] = v

            try {
                // Interpret opcode
                switch (opword >> 8) {
                    // Cases ordered as CPython to aid comparison

                    case Opcode.NOP:
                        break;

                    case Opcode.LOAD_CONST:
                        s[sp++] = consts[oparg | opword & 0xff];
                        oparg = 0;
                        break;

                    case Opcode.UNARY_NEGATIVE:
                        s[sp - 1] = PyNumber.negative(s[sp - 1]);
                        break;

                    case Opcode.UNARY_INVERT:
                        s[sp - 1] = PyNumber.invert(s[sp - 1]);
                        break;

                    case Opcode.BINARY_MULTIPLY:
                        w = s[--sp]; // POP
                        s[sp - 1] = PyNumber.multiply(s[sp - 1], w);
                        break;

                    case Opcode.BINARY_ADD:
                        w = s[--sp]; // POP
                        s[sp - 1] = PyNumber.add(s[sp - 1], w);
                        break;

                    case Opcode.BINARY_SUBTRACT:
                        w = s[--sp]; // POP
                        s[sp - 1] = PyNumber.subtract(s[sp - 1], w);
                        break;

                    case Opcode.BINARY_SUBSCR: // w[v]
                        // w | v | -> | w[v] |
                        // -------^sp --------^sp
                        v = s[--sp]; // POP
                        s[sp - 1] = PySequence.getItem(s[sp - 1], v);
                        break;

                    case Opcode.STORE_SUBSCR: // w[v] = u
                        // u | w | v | -> |
                        // -----------^sp -^sp
                        sp -= 3; // STACK_SHRINK(3);
                        // setItem(w, v, u)
                        PySequence.setItem(s[sp + 1], s[sp + 2], s[sp]);
                        break;

                    case Opcode.RETURN_VALUE:
                        returnValue = s[--sp]; // POP
                        // ip = END; ?
                        break loop;

                    case Opcode.STORE_NAME:
                        name = names[oparg | opword & 0xff];
                        oparg = 0;
                        try {
                            locals.put(name, s[--sp]);
                        } catch (NullPointerException npe) {
                            throw new SystemError(
                                    "no locals found when storing '%s'",
                                    name);
                        }
                        break;

                    case Opcode.UNPACK_SEQUENCE:
                        // w | -> w[n-1] | ... | w[0] |
                        // ---^sp ---------------------^sp
                        oparg |= opword & 0xff; // n
                        w = s[--sp];
                        if (w instanceof PyTuple
                                || w instanceof PyList) {
                            List<?> seq = (List<?>)w;
                            if (seq.size() == oparg) {
                                int i = sp + oparg;
                                for (Object o : seq) { s[--i] = o; }
                                sp += oparg;
                                oparg = 0;
                                break;
                            }
                            // Wrong size: slow path to error message
                        }
                        // unpack iterable w to s[sp...sp+n]
                        sp = unpackIterable(w, oparg, -1, s, sp);
                        oparg = 0;
                        break;

                    case Opcode.UNPACK_EX:
                        // w | -> w[N-1] | ... | w[0] |
                        // ---^sp ---------------------^sp
                        n = opword & 0xff;
                        m = oparg >> 8;
                        oparg = 0;
                        sp = unpackIterable(s[--sp], n, m, s, sp);
                        break;

                    case Opcode.LOAD_NAME:
                        name = names[oparg | opword & 0xff];
                        oparg = 0;
                        try {
                            v = locals.get(name);
                        } catch (NullPointerException npe) {
                            throw new SystemError(
                                    "no locals found when loading '%s'",
                                    name);
                        }

                        if (v == null) {
                            v = globals.get(name);
                            if (v == null) {
                                v = builtins.get(name);
                                if (v == null)
                                    throw new NameError(NAME_ERROR_MSG,
                                            name);
                            }
                        }
                        s[sp++] = v; // PUSH
                        break;

                    case Opcode.BUILD_TUPLE:
                        // w[0] | ... | w[oparg-1] | -> | tpl |
                        // -------------------------^sp -------^sp
                        // Group the N=oparg elements on the stack
                        // into a single tuple.
                        oparg |= opword & 0xff;
                        sp -= oparg;
                        s[sp] = new PyTuple(s, sp++, oparg);
                        oparg = 0;
                        break;

                    case Opcode.BUILD_LIST:
                        // w[0] | ... | w[oparg-1] | -> | lst |
                        // -------------------------^sp -------^sp
                        // Group the N=oparg elements on the stack
                        // into a single list.
                        oparg |= opword & 0xff;
                        sp -= oparg;
                        s[sp] = new PyList(s, sp++, oparg);
                        oparg = 0;
                        break;

                    // case Opcode.BUILD_TUPLE_UNPACK_WITH_CALL:

                    case Opcode.BUILD_TUPLE_UNPACK:
                        // w[0] | ... | w[oparg-1] | -> | sum |
                        // -------------------------^sp -------^sp
                        // Concatenate the N=oparg iterables on the
                        // stack into a single tuple.
                        oparg |= opword & 0xff;
                        sp -= oparg;
                        tpl = new PyTuple.Builder();
                        for (int i = 0; i < oparg; i++) {
                            tpl.append((List<?>)s[sp + i]);
                        }
                        s[sp++] = tpl.take();
                        oparg = 0;
                        break;

                    case Opcode.BUILD_LIST_UNPACK:
                        // w[0] | ... | w[oparg-1] | -> | sum |
                        // -------------------------^sp -------^sp
                        // Concatenate the N=oparg iterables on the
                        // stack into a single list.
                        oparg |= opword & 0xff;
                        sp -= oparg;
                        lst = new PyList();
                        for (int i = 0; i < oparg; i++) {
                            lst.addAll((List<?>)s[sp + i]);
                        }
                        s[sp++] = lst;
                        oparg = 0;
                        break;

                    case Opcode.BUILD_MAP:
                        // k1 | v1 | ... | kN | vN | -> | map |
                        // -------------------------^sp -------^sp
                        // Build dictionary from the N=oparg key-value
                        // pairs on the stack in order.
                        oparg |= opword & 0xff;
                        sp -= oparg * 2;
                        s[sp] = PyDict.fromKeyValuePairs(s, sp++,
                                oparg);
                        oparg = 0;
                        break;

                    case Opcode.LOAD_ATTR:
                        // v | -> | v.name |
                        // ---^sp ----------^sp
                        name = names[oparg | opword & 0xff];
                        oparg = 0;
                        s[sp - 1] = Abstract.getAttr(s[sp - 1], name);
                        break;

                    case Opcode.COMPARE_OP:
                        w = s[--sp]; // POP
                        v = s[sp - 1]; // TOP
                        s[sp - 1] = Comparison.from(opword & 0xff)
                                .apply(v, w);
                        oparg = 0;
                        break;

                    case Opcode.JUMP_FORWARD:
                        ip += (oparg | opword & 0xff) >> 1;
                        oparg = 0;
                        break;

                    case Opcode.POP_JUMP_IF_FALSE:
                        v = s[--sp]; // POP
                        if (!Abstract.isTrue(v))
                            ip = ((oparg | opword & 0xff) >> 1) - 1;
                        oparg = 0;
                        break;

                    case Opcode.POP_JUMP_IF_TRUE:
                        v = s[--sp]; // POP
                        if (Abstract.isTrue(v))
                            ip = ((oparg | opword & 0xff) >> 1) - 1;
                        oparg = 0;
                        break;

                    case Opcode.JUMP_IF_FALSE_OR_POP:
                        v = s[--sp]; // POP
                        if (!Abstract.isTrue(v)) {
                            sp += 1;    // UNPOP
                            ip = ((oparg | opword & 0xff) >> 1) - 1;
                        }
                        oparg = 0;
                        break;

                    case Opcode.JUMP_IF_TRUE_OR_POP:
                        v = s[--sp]; // POP
                        if (Abstract.isTrue(v)) {
                            sp += 1;    // UNPOP
                            ip = ((oparg | opword & 0xff) >> 1) - 1;
                        }
                        oparg = 0;
                        break;

                    case Opcode.JUMP_ABSOLUTE:
                        ip = ((oparg | opword & 0xff) >> 1) - 1;
                        oparg = 0;
                        break;

                    case Opcode.LOAD_METHOD:
                        // Designed to work in tandem with CALL_METHOD.
                        // If we can bypass temporary bound method:
                        // obj | -> | desc | self |
                        // -----^sp ---------------^sp
                        // Otherwise almost conventional LOAD_ATTR:
                        // obj | -> | null | meth |
                        // -----^sp ---------------^sp
                        name = names[oparg | opword & 0xff];
                        oparg = 0;
                        getMethod(s[--sp], name, sp);
                        sp += 2;
                        break;

                    case Opcode.CALL_METHOD:
                        // Designed to work in tandem with LOAD_METHOD.
                        // If bypassed the method binding:
                        // desc | self | arg[n] | -> | res |
                        // ----------------------^sp -------^sp
                        // Otherwise:
                        // null | meth | arg[n] | -> | res |
                        // ----------------------^sp -------^sp
                        oparg |= opword & 0xff; // = N of args
                        sp -= oparg + 2;
                        if (s[sp] != null) {
                            // We bypassed the method binding. Stack:
                            // desc | self | arg[n] |
                            // ^sp
                            // call desc(self, arg1 ... argN)
                            s[sp] = Callables.vectorcall(s[sp++], s, sp,
                                    oparg + 1);
                        } else {
                            // meth is the bound method self.name
                            // null | meth | arg[n] |
                            // ^sp
                            // call meth(arg1 ... argN)
                            s[sp++] = Callables.vectorcall(s[sp], s,
                                    sp + 1, oparg);
                        }
                        oparg = 0;
                        break;

                    case Opcode.CALL_FUNCTION:
                        // Call with positional args only. Stack:
                        // f | arg[n] | -> res |
                        // ------------^sp -----^sp
                        oparg |= opword & 0xff; // = N of args
                        sp -= oparg + 1;
                        s[sp] = Callables.vectorcall(s[sp++], s, sp,
                                oparg);
                        oparg = 0;
                        break;

                    case Opcode.CALL_FUNCTION_KW: {
                        // Call with n positional & m by kw. Stack:
                        // f | arg[n] | kwnames | -> res |
                        // ----------------------^sp -----^sp
                        // knames is a tuple of m names
                        assert PyTuple.TYPE.checkExact(s[sp - 1]);
                        PyTuple kwnames = (PyTuple)s[sp - 1];
                        oparg |= opword & 0xff; // = n+m
                        assert kwnames.size() <= oparg;
                        sp -= oparg + 2;
                        s[sp] = Callables.vectorcall(s[sp++], s, sp,
                                oparg, kwnames);
                        oparg = 0;
                        break;
                    }

                    case Opcode.CALL_FUNCTION_EX:
                        // Call with positional & kw args. Stack:
                        // f | args | kwdict? | -> res |
                        // ---------------------^sp -----^sp
                        // opword is 0 (no kwdict) or 1 (kwdict present)
                        w = (opword & 0x1) == 0 ? null : s[--sp];
                        v = s[--sp]; // args tuple
                        s[sp - 1] = Callables.callEx(s[sp - 1], v, w);
                        oparg = 0;
                        break;

                    case Opcode.EXTENDED_ARG:
                        /*
                         * This opcode extends the effective opcode
                         * argument of the next opcode that has one.
                         */
                        // before: ........xxxxxxxx00000000
                        // after : xxxxxxxxaaaaaaaa00000000
                        oparg = (oparg | opword & 0xff) << 8;
                        /*
                         * When we encounter an argument to a "real"
                         * opcode, we need only mask it to 8 bits and or
                         * it with the already aligned oparg prefix.
                         * Every opcode that consumes oparg must set it
                         * to zero to reset this logic.
                         */
                        break;

                    default:
                        throw new InterpreterError("ip: %d, opcode: %d",
                                2 * ip, opword >> 8);
                } // switch

            } catch (PyException pye) {
                /*
                 * We ought here to check for exception handlers
                 * (defined in Python and reflected in the byte code)
                 * potentially resuming the loop with ip at the handler
                 * code, or in a Python finally clause.
                 */
                // Should handle within Python, but for now, stop.
                System.err.println(pye);
                throw pye;
            } catch (InterpreterError | AssertionError ie) {
                /*
                 * An InterpreterError signals an internal error,
                 * recognised by our implementation: stop.
                 */
                throw ie;
            } catch (Throwable t) {
                /*
                 * A non-Python exception signals an internal error, in
                 * our implementation, in user-supplied Java, or from a
                 * Java library misused from Python.
                 */
                // Should handle within Python, but for now, stop.
                t.printStackTrace();
                throw new InterpreterError(t,
                        "Non-PyException at ip: %d, opcode: %d", 2 * ip,
                        opword >> 8);
            }
        } // loop

        // ThreadState.get().swap(back);
        return returnValue;
    }

    // Supporting definitions and methods -----------------------------

    private static final PyCell[] EMPTY_CELL_ARRAY = PyCell.EMPTY_ARRAY;

    private static final String NAME_ERROR_MSG =
            "name '%.200s' is not defined";
    private static final String UNPACK_EXPECTED_AT_LEAST =
            "not enough values to unpack (expected at least %d, got %d)";
    private static final String UNPACK_EXPECTED =
            "not enough values to unpack (expected %d, got %d)";
    private static final String TOO_MANY_TO_UNPACK =
            "too many values to unpack (expected %d)";

    /**
     * Store the elements of a Python iterable in a slice
     * {@code [sp:sp+n+m+1]} of an array (the stack) in reverse order of
     * their production. This exists to support
     * {@link Opcode#UNPACK_SEQUENCE} and {@link Opcode#UNPACK_EX}.
     * <p>
     * {@code UNPACK_SEQUENCE} is the compiled form of an unpacking to
     * variables like:<pre>
     * a, b, c = values
     * </pre> In this case, {@code argcnt == 3} and
     * {@code argcntafter == -1}.
     * <p>
     * {@code UNPACK_EX} is the compiled form of an unpacking to
     * variables like:<pre>
     * a, *b, c, d = values
     * </pre> In this case, {@code argcnt == 1} and
     * {@code argcntafter == 2}.
     * <p>
     * There are always {@code N == n + m + 1} arguments in total, and
     * they will land in elements {@code s[sp] ... s[sp+N-1]}, with the
     * list (if {@code m >= 0}) at {@code s[sp+m]}, with {@code m}
     * "after" arguments before it, and the {@code n} "before" arguments
     * after it. (They're stacked in reverse, remember, so that they
     * un-stack in the left-to-right order of the assignment targets.)
     * The return value is {@code sp+N}.
     *
     * @param v the claimed iterable object
     * @param argcnt expected number of "before" arguments
     * @param argcntafter expected number of "after" arguments or -1
     * @param s stack array
     * @param sp stack pointer <b>before push</b>
     * @return stack pointer after pushing all the values
     * @throws TypeError if {@code v} is not iterable
     * @throws ValueError if the number of arguments from the itearable
     *     is not as expected.
     * @throws Throwable from the implementation of {@code v}.
     */
    // Compare CPython unpack_iterable in ceval.c (args differ)
    private static int unpackIterable(Object v, int argcnt,
            int argcntafter, Object[] s, int sp)
            throws TypeError, ValueError, Throwable {

        assert argcntafter >= -1;
        sp += argcnt + 1 + argcntafter;
        int count = 0, top = sp;

        Object it = Abstract.getIterator(v, () -> Abstract.typeError(
                "cannot unpack non-iterable %.200s object", v));

        for (; count < argcnt; count++) {
            Object w = Abstract.next(it);
            if (w == null) {
                // Reached end
                if (argcntafter == -1) {
                    throw new ValueError(UNPACK_EXPECTED, argcnt,
                            count);
                } else {
                    throw new ValueError(UNPACK_EXPECTED_AT_LEAST,
                            argcnt + argcntafter, count);
                }
            }
            s[--sp] = w;
        }

        if (argcntafter == -1) {
            // We should have exhausted the iterator now.
            if (Abstract.next(it) != null) {
                throw new ValueError(TOO_MANY_TO_UNPACK, argcnt);
            }

        } else {

            PyList list = PySequence.list(it);
            s[--sp] = list;
            count++;

            int len = list.size();
            if (len < argcntafter) {
                throw new ValueError(UNPACK_EXPECTED_AT_LEAST,
                        argcnt + argcntafter, argcnt + len);
            }

            // Pop the "after-variable" args off list.
            for (int j = argcntafter; j > 0; j--, count++) {
                s[--sp] = list.get(len - j);
            }
            // Resize the list.
            list.subList(len - argcntafter, len).clear();
        }
        return top;
    }

    /**
     * A specialised version of
     * {@link PyBaseObject#__getattribute__(Object, String)
     * object.__getattribute__} specifically to support the
     * {@code LOAD_METHOD} and {@code CALL_METHOD} opcode pair generated
     * by the CPython byte code compiler. This method will place two
     * entries in the stack at the offset given that are either:
     * <ol>
     * <li>an unbound method and the object passed ({@code obj}),
     * or</li>
     * <li>{@code null} and a bound method object.</li>
     * </ol>
     * <p>
     * The normal behaviour of {@code object.__getattribute__} is
     * represented by case 2.
     * <p>
     * Case 1 supports an optimisation that is possible when the type of
     * the self object {@code obj} has not overridden
     * {@code __getattribute__}, and the {@code name} resolves to a
     * regular method in it. {@code CALL_METHOD} will detect and use
     * this optimised form if the first element is not {@code null}.
     *
     * @param obj of whichg the callable is an attribute
     * @param name of callable attribute
     * @param offset in stack at which to place results
     * @throws AttributeError ifthe named attribute does not exist
     * @throws Throwable from other errors
     */
    // Compare CPython _PyObject_GetMethod in object.c
    private void getMethod(Object obj, String name, int offset)
            throws AttributeError, Throwable {

        PyType objType = PyType.of(obj);

        // If type(obj) defines its own __getattribute__ use that.
        if (!objType.hasGenericGetAttr()) {
            valuestack[offset] = null;
            valuestack[offset + 1] = Abstract.getAttr(obj, name);
            return;
        }

        /*
         * From here, the code is a version of the default attribute
         * access mechanism PyBaseObject.__getattribute__ in which, if
         * the look-up leads to a method descriptor, we avoid binding
         * the descriptor into a short-lived bound method object.
         */

        MethodHandle descrGet = null;
        boolean methFound = false;

        // Look up the name in the type (null if not found).
        Object typeAttr = objType.lookup(name);
        if (typeAttr != null) {
            // Found in the type, it might be a descriptor
            Operations typeAttrOps = Operations.of(typeAttr);
            descrGet = typeAttrOps.op_get;
            if (typeAttrOps.isMethodDescr()) {
                /*
                 * We found a method descriptor, but will check the
                 * instance dictionary for a shadowing definition.
                 */
                methFound = true;
            } else if (typeAttrOps.isDataDescr()) {
                // typeAttr is a data descriptor so call its __get__.
                try {
                    valuestack[offset] = null;
                    valuestack[offset + 1] = descrGet
                            .invokeExact(typeAttr, obj, objType);
                    return;
                } catch (Slot.EmptyException e) {
                    /*
                     * Only __set__ or __delete__ was defined. We do not
                     * catch AttributeError: it's definitive. Suppress
                     * trying __get__ again.
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
        if (obj instanceof DictPyObject) {
            Map<Object, Object> d = ((DictPyObject)obj).getDict();
            Object instanceAttr = d.get(name);
            if (instanceAttr != null) {
                // Found the callable in the instance dictionary.
                valuestack[offset] = null;
                valuestack[offset + 1] = instanceAttr;
                return;
            }
        }

        /*
         * The name wasn't in the instance dictionary (or there wasn't
         * an instance dictionary). typeAttr is the result of look-up on
         * the type: a value , a non-data descriptor, or null if the
         * attribute was not found.
         */
        if (methFound) {
            /*
             * typeAttr is a method descriptor and was not shadowed by
             * an entry in the instance dictionary.
             */
            valuestack[offset] = typeAttr;
            valuestack[offset + 1] = obj;
            return;
        } else if (descrGet != null) {
            // typeAttr may be a non-data descriptor: call __get__.
            try {
                valuestack[offset] = null;
                valuestack[offset + 1] =
                        descrGet.invokeExact(typeAttr, obj, objType);
                return;
            } catch (Slot.EmptyException e) {}
        }

        if (typeAttr != null) {
            /*
             * The attribute obtained from the type, and that turned out
             * not to be a descriptor, is the callable.
             */
            valuestack[offset] = null;
            valuestack[offset + 1] = typeAttr;
            return;
        }

        // All the look-ups and descriptors came to nothing :(
        throw Abstract.noAttributeError(obj, name);
    }
}
