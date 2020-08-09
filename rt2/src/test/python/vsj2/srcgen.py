# Classes that emit indented, wrapped (Java) source code for tests

import io
import sys
import dis


class IndentedEmitter:
    """Class to write wrapped, indented (program) text onto a stream.

    Text is supplied via the emit() and emit_line() methods, and added to
    an internal buffer. emit_line() writes the current buffer (if it is not
    empty), always beginning a new, indented line. emit() first checks for
    sufficient buffer space, writing existing content to the output stream
    only as necessary to respect the stated width. The supplied text is
    treated as atomic, however long: neither method inserts line-breaks.
    close() must be called to ensure the last buffered text reaches the
    output stream. (Consider using contextlib.closing.)
    """

    class IndentationContextManager:
        """Context in which the indentation is increased by one."""

        def __init__(self, emitter):
            self.emitter = emitter

        def __enter__(self):
            self.emitter.indent += 1
            return self

        def __exit__(self, exc_type, exc_val, exc_tb):
            self.emitter.indent -= 1

    def indentation(self):
        """Return a context manager to increase the indentation by one."""
        return JavaConstantEmitter.IndentationContextManager(self)

    def __init__(self, stream=None, width=None, indent=None):
        self.stream = stream or sys.stdout
        self.width = width if width is not None else 70
        self.indent = indent if indent is not None else 1
        # Output buffer when lines are pieced together
        self.buf = io.StringIO()

    def flush(self):
        """Emit residual line (if any) to the output stream."""
        residue = self.buf.getvalue().rstrip()
        if residue:
            print(residue, file=self.stream)
        self.buf.seek(0)
        self.buf.truncate()

    close = flush  # synonym for the benefit of "with closing(...)"

    def emit(self, text="", suffix=""):
        """Write the text+suffix to self.buf, on a new line if necessary."""
        n = len(text)
        if suffix:
            n += len(suffix)
        if self.buf.tell() + n > self.width:
            # Must start a new line first
            self.emit_line()
        self.buf.write(text)
        if suffix:
            self.buf.write(suffix)
        return self

    def emit_line(self, text=""):
        """Begin a new line with indent and optional text."""
        if self.buf.tell() > 0:
            # Flush existing buffer to output
            print(self.buf.getvalue().rstrip(), file=self.stream)
        self.buf.seek(0)
        self.buf.truncate()
        for i in range(self.indent):
            self.buf.write("    ")
        self.buf.write(text)
        return self


class JavaConstantEmitter(IndentedEmitter):
    """A class capable of emitting Java constants equivalent to Python values.

    This class extends the basic IndentedEmitter for wrapped, indented
    program text with methods that translate Python values to equivalent
    Java constants (or constructor expressions). Each
    """

    MAX_INT = (1 << 31) - 1
    MIN_INT = -MAX_INT - 1

    def java_int(self, value, suffix=""):
        """Emit the value as a Java int constant."""
        if self.MIN_INT <= value <= self.MAX_INT:
            return self.emit(repr(value) + suffix)
        else:
            raise ValueError("Value out of range for Java int")

    def java_string(self, value, suffix=""):
        """Emit the value as a Java String constant."""
        text = repr(str(value))
        if text.startswith("'"):
            q = '"'
            text = q + text[1:-1].replace(q, '\\"') + q
        return self.emit(text, suffix)

    def java_byte(self, value, suffix=""):
        """Emit the value as a Java int constant wrapped to signed byte."""
        bstr = format(value if value < 128 else value - 256, "d")
        return self.emit(bstr, suffix)

    def java_double(self, value, suffix=""):
        """Emit the value as a Java double constant."""
        return self.emit(repr(value), suffix)

    def java_arglist(self, handler, a, suffix=""):
        """Emit comma-separated Java values using the given handler."""
        n = len(a)
        if n == 0:
            self.emit(suffix)
        else:
            with self.indentation():
                for i in range(n-1):
                    handler(a[i], ", ")
                handler(a[-1], suffix)
        return self

    def java_array(self, handler, a, suffix=""):
        """Emit a Java array of elements emitted by the given handler."""
        n = len(a)
        if n == 0:
            self.emit("{}", suffix)
        else:
            self.emit("{ ")
            with self.indentation():
                for i in range(n-1):
                    handler(a[i], ", ")
                handler(a[-1], " }" + suffix)
        return self


class PythonEmitter(JavaConstantEmitter):
    """An abstract class capable of emitting Python values as Java objects.

    This class extends the JavaConstantEmitter with methods that translate
    Python values to equivalent to expressions that construct PyObjects
    equivalent to them, on the assumption of a particular run-time
    environment.
    """

    def __init__(self, special_handlers=None, **kwds):
        self._handlers = special_handlers or dict()
        super().__init__(**kwds)

    def add_special_handlers(self, handlers):
        """Map particular object values to f(obj, suffix)"""
        self._handlers.update(handlers)

    def python(self, obj, suffix=""):
        """Emit Java to construct a Python value of any supported type."""
        handler = self._handlers.get(id(obj))
        if handler:
            handler(self, obj, suffix)
        else:
            t = type(obj)
            handler = getattr(self, "python_" + t.__name__, None)
            if handler:
                handler(obj, suffix)
            else:
                self.java_string(repr(t), suffix)
        return self

    # Override the following at least
    def python_str(self, value, suffix=""): return self
    def python_int(self, value, suffix=""): return self
    def python_bool(self, value, suffix=""): return self
    def python_float(self, value, suffix=""): return self
    def python_NoneType(self, value, suffix=""): return self
    def python_bytes(self, value, suffix=""): return self
    def python_tuple(self, value, suffix=""): return self
    def python_list(self, value, suffix=""): return self
    def python_code(self, code, suffix=""): return self


class PyObjectEmitter(PythonEmitter):
    """A class capable of emitting Python values as PyObjects.

    This class extends the JavaConstantEmitter with methods that translate
    Python values to equivalent to expressions that construct PyObjects
    equivalent to them, on the assumption of the particular run-time
    environment explored in Java test PyByteCode1.
    """
    def __init__(self, code_comment=False, **kwds):
        self.code_comment = code_comment
        super().__init__(**kwds)

    def python_str(self, value, suffix=""):
        """Emit Java to construct a Python str."""
        text = repr(value)
        if text.startswith("'"):
            q = '"'
            text = q + text[1:-1].replace(q, '\\"') + q
        return self.emit(f"new PyUnicode({text})", suffix)

    def python_int(self, value, suffix=""):
        """Emit Java to construct a Python int."""
        if value > self.MAX_INT or value < self.MIN_INT:
            value = '"{:d}"'.format(value)
        text = f"new PyLong({value})"
        return self.emit(text, suffix)

    def python_bool(self, value, suffix=""):
        """Emit Java to construct a Python bool."""
        text = "PyBool.True" if value else "PyBool.False"
        return self.emit(text, suffix)

    def python_float(self, value, suffix=""):
        """Emit Java to construct a Python float."""
        text = f"new PyFloat({value!r})"
        return self.emit(text, suffix)

    def python_NoneType(self, value, suffix=""):
        """Emit Java to reference None."""
        return self.emit("Py.None", suffix)

    def python_bytes(self, value, suffix=""):
        """Emit Java to construct a Python bytes."""
        self.emit_line("new PyBytes(")
        with self.indentation():
            self.emit("new byte[] ")
            self.java_array(self.java_byte, value, ")" + suffix)
        return self

    def python_tuple(self, value, suffix=""):
        """Emit Java to construct a Python tuple."""
        self.emit_line("new PyTuple(")
        with self.indentation():
            self.emit("new PyObject[] ")
            self.java_array(self.python, value, ")" + suffix)
        return self

    def python_list(self, value, suffix=""):
        """Emit Java to construct a Python list."""
        self.emit_line("new PyList(")
        with self.indentation():
            self.emit("new PyObject[] ")
            self.java_array(self.python, value, ")" + suffix)
        return self

    def python_code(self, code, suffix=""):
        """Emit Java to construct a Python code object.

        The assumed constructor exactly emulates CPython codeobject.c
        PyCode_NewWithPosOnlyArgs, in turn assuming there are constructors
        for Java implementations of the types depended upon.
        """
        if self.code_comment:
            # Emit disassembly as comment
            self.emit_line("/*")
            lines = dis.Bytecode(code).dis().splitlines()
            for line in lines:
                self.emit_line(" * " + line)
            self.emit_line(" */")
            self.emit_line("")

        self.emit("new PyCode(")
        with self.indentation():
            self.java_int(code.co_argcount, ", ")
            self.java_int(code.co_posonlyargcount, ", ")
            self.java_int(code.co_kwonlyargcount, ", ")
            self.java_int(code.co_nlocals, ", ")
            self.java_int(code.co_stacksize, ", ")
            self.java_int(code.co_flags, ", ")
            self.python(code.co_code, ", ")
            self.python(code.co_consts, ", ")
            self.python(code.co_names, ", ")
            self.python(code.co_varnames, ", ")
            self.python(code.co_freevars, ", ")
            self.python(code.co_cellvars, ", ")
            self.python(code.co_filename, ", ")
            self.python(code.co_name, ", ")
            self.java_int(code.co_firstlineno, ", ")
            self.python(code.co_lnotab, ")" + suffix)
        return self


class PyObjectTestEmitter:
    """Class to emit a test PyCode and a JUnit test method for each case.

    The generated code assumes a particular representation for Python in Java,
    which is introduced in PyByteCode1.java and pursued in Java package evo2.
    """

    def __init__(self, test, writer=None):
        self.writer = PyObjectEmitter() if writer is None else writer
        self.test = test
        # Compile the lines to byte code
        prog = '\n'.join(test.body)
        code = compile(prog, self.test.name, 'exec')
        self.bytecode = dis.Bytecode(code)

    def emit_line(self, text=""):
        """Begin a new line on the writer with indent and optional text."""
        self.writer.emit_line(text)

    def close(self):
        """Flush and close this test emitter."""
        self.writer.close()

    def emit_test_material(self):
        """Emit the PyCode comments, declaration and initialiser."""
        self.emit_comments()
        self.emit_declaration()
        return self

    def emit_test_cases(self):
        """Emit the test methods."""
        num = 0
        for c in self.test.cases:
            num += 1
            name = "test_{}{:d}".format(self.test.name, num)
            self.emit_test_method(name, c)
        return self

    def emit_comments(self):
        """Emit the comments that precede the PyCode declaration"""
        self.writer.emit_line("/**")
        self.writer.emit_line(f" * Example '{self.test.name}': <pre>")
        for line in self.test.body:
            self.writer.emit_line(f" * {line}")
        self.writer.emit_line(" * </pre>")
        return self.writer.emit_line(" */")

    def emit_declaration(self):
        """Emit the PyCode declaration and initialiser"""
        self.writer.emit_line("//@formatter:off")
        self.writer.emit_line("static final PyCode ")
        self.writer.emit(self.test.name.upper(), " = ")
        self.writer.python_code(self.bytecode.codeobj, ";")
        self.writer.emit_line("//@formatter:on")
        return self.writer.emit_line()

    def emit_test_method(self, name, c):
        """Emit one JUnit test method with the given name"""
        # Prepare the "before" name space by executing the "case" code
        before = dict()
        exec(c, {}, before)
        # Execute the example code against a copy of that name space
        globals = dict(before)
        exec(self.bytecode.codeobj, globals)
        # Extract those variables names as results to test
        after = {k: globals[k] for k in self.test.test}
        # Check
        #print("before = {!r}".format(before))
        #print("after = {!r}".format(after))
        # Emit the code for the test method
        self.writer.emit_line("@Test")
        self.writer.emit_line("void " + name + "() {")
        with self.writer.indentation():
            self.writer.emit_line("//@formatter:off")
            # Load the global name space with the test case values
            self.writer.emit_line("PyDictionary globals = new PyDictionary();")
            for k, v in before.items():
                self.writer.emit_line("globals.put(")
                self.writer.python(k, ", ")
                self.writer.python(v, ");")
            # Execute the code in the Java implementation
            self.writer.emit_line("PyCode code = " + self.test.name.upper() + ";")
            self.writer.emit_line("ThreadState tstate = new ThreadState();")
            self.writer.emit_line(
                "PyFrame frame = code.createFrame(tstate, globals, globals);")
            self.writer.emit_line("frame.eval();")
            # Compare named results against the values this Python got
            for k, v in after.items():
                msg = "{} == {}".format(k, repr(v))
                self.writer.emit_line("assertEquals(")
                with self.writer.indentation():
                    self.writer.python(v, ", ")
                    self.writer.emit("globals.get(")
                    self.writer.python(k, "), ")
                    self.writer.java_string(msg, ");")
            self.writer.emit_line("//@formatter:on")
        self.writer.emit_line("}")
        return self.writer.emit_line()


class PyObjectEmitterEvo3(PyObjectEmitter):
    """A class capable of emitting Python values as PyObjects (short variant).

    This class extends PyObjectEmitter, replacing the methods that emit
    constructors with equivalent calls to static runtime methods. This
    matches the run-time environment explored in Java package evo3.
    """
    def python_str(self, value, suffix=""):
        """Emit Java to construct a Python str."""
        text = repr(value)
        if text.startswith("'"):
            q = '"'
            text = q + text[1:-1].replace(q, '\\"') + q
        return self.emit(f"Py.str({text})", suffix)

    def python_int(self, value, suffix=""):
        """Emit Java to construct a Python int."""
        if value > self.MAX_INT or value < self.MIN_INT:
            value = '"{:d}"'.format(value)
        text = f"Py.val({value})"
        return self.emit(text, suffix)

    def python_bool(self, value, suffix=""):
        """Emit Java to construct a Python bool."""
        text = "Py.True" if value else "Py.False"
        return self.emit(text, suffix)

    def python_float(self, value, suffix=""):
        """Emit Java to construct a Python float."""
        text = f"Py.val({value!r})"
        return self.emit(text, suffix)

    def python_bytes(self, value, suffix=""):
        """Emit Java to construct a Python bytes."""
        if len(value) == 0:
            self.emit("Py.bytes()", suffix)
        else:
            self.emit_line("Py.bytes(")
            self.java_arglist(self.java_byte, value, ")" + suffix)
            return self

    def python_tuple(self, value, suffix=""):
        """Emit Java to construct a Python tuple."""
        self.emit_line("Py.tuple(")
        self.java_arglist(self.python, value, ")" + suffix)
        return self

    def python_list(self, value, suffix=""):
        """Emit Java to construct a Python list."""
        self.emit_line("Py.list(")
        self.java_arglist(self.python, value, ")" + suffix)
        return self

    def python_code(self, code, suffix=""):
        """Emit Java to construct a Python code object.

        The assumed constructor exactly emulates CPython codeobject.c
        PyCode_NewWithPosOnlyArgs, in turn assuming there are constructors
        for Java implementations of the types depended upon.
        """
        if self.code_comment:
            # Emit disassembly as comment
            self.emit_line("/*")
            lines = dis.Bytecode(code).dis().splitlines()
            for line in lines:
                self.emit_line(" * " + line)
            self.emit_line(" */")
            self.emit_line("")

        self.emit("new CPythonCode(")
        with self.indentation():
            self.java_int(code.co_argcount, ", ")
            self.java_int(code.co_posonlyargcount, ", ")
            self.java_int(code.co_kwonlyargcount, ", ")
            self.java_int(code.co_nlocals, ", ")
            self.java_int(code.co_stacksize, ", ")
            self.java_int(code.co_flags, ", ")
            self.python(code.co_code, ", ")
            self.python(code.co_consts, ", ")
            self.python(code.co_names, ", ")
            self.python(code.co_varnames, ", ")
            self.python(code.co_freevars, ", ")
            self.python(code.co_cellvars, ", ")
            self.python(code.co_filename, ", ")
            self.python(code.co_name, ", ")
            self.java_int(code.co_firstlineno, ", ")
            self.python(code.co_lnotab, ")" + suffix)
        return self


class PyObjectTestEmitterEvo3(PyObjectTestEmitter):
    """Class to emit a test PyCode and a JUnit test method for each case.

    The generated code assumes a particular representation for Python in Java,
    which matches the run-time environment explored in Java package evo3.
    """

    def __init__(self, test, writer=None):
        self.writer = PyObjectEmitterEvo3() if writer is None else writer
        self.test = test
        # Compile the lines to byte code
        prog = '\n'.join(test.body)
        code = compile(prog, self.test.name, 'exec')
        self.bytecode = dis.Bytecode(code)

    def emit_test_method(self, name, c):
        """Emit one JUnit test method with the given name"""
        # Prepare the "before" name space by executing the "case" code
        before = dict()
        exec(c, {}, before)
        # Execute the example code against a copy of that name space
        globals = dict(before)
        exec(self.bytecode.codeobj, globals)
        # Extract those variables names as results to test
        after = {k: globals[k] for k in self.test.test}
        # Check
        #print("before = {!r}".format(before))
        #print("after = {!r}".format(after))
        # Emit the code for the test method
        self.writer.emit_line("@Test")
        self.writer.emit_line("void " + name + "() {")
        with self.writer.indentation():
            self.writer.emit_line("//@formatter:off")
            # Load the global name space with the test case values
            self.writer.emit_line("PyDict globals = Py.dict();")
            for k, v in before.items():
                self.writer.emit_line("globals.put(")
                with self.writer.indentation():
                    self.writer.java_string(k, ", ")
                    self.writer.python(v, ");")
            # Execute the code in the Java implementation
            self.writer.emit_line("Interpreter interp = Py.createInterpreter();")
            self.writer.emit_line("interp.evalCode(")
            self.writer.emit(self.test.name.upper())
            self.writer.emit(", globals, globals);")
            # Compare named results against the values this Python got
            for k, v in after.items():
                msg = "{} == {}".format(k, repr(v))
                self.writer.emit_line("assertEquals(")
                with self.writer.indentation():
                    self.writer.python(v, ", ")
                    self.writer.emit("globals.get(")
                    self.writer.java_string(k, "), ")
                    self.writer.java_string(msg, ");")
            self.writer.emit_line("//@formatter:on")
        self.writer.emit_line("}")
        return self.writer.emit_line()


class PyObjectEmitterEvo4(PyObjectEmitterEvo3):
    """A class capable of emitting Python values as PyObjects (short variant).

    This class extends PyObjectEmitter, replacing the methods that emit
    constructors with equivalent calls to static runtime methods. This
    matches the run-time environment explored in Java package evo4.
    """
    MAX_LONG = (1 << 63) - 1
    MIN_LONG = -MAX_LONG - 1

    def python_int(self, value, suffix=""):
        """Emit Java to construct a Python int."""
        if self.MIN_INT <= value <= self.MAX_INT:
            text = f"Py.val({value})"
        elif self.MIN_LONG <= value <= self.MAX_LONG:
            text = f"Py.val({value}L)"
        else:
            text = f"Py.val(new BigInteger(\"{value}\"))"
        return self.emit(text, suffix)


# Used by PyObjectTestEmitterEvo4
def _make_handlers():
    # Make a lookup from ids of builtins values to an access expression
    snitliub = dict()
    builtins = __builtins__
    # __builtins__ may be a module or a dictionary
    if not isinstance(builtins, dict):
        builtins = builtins.__dict__
    names = set(builtins.keys())
    # Edit out objects we handle another way
    #names -= set(('None', 'True', 'False', '__debug__'))
    names -= {'__spec__', '__package__', '__name__', 'credits', 'copyright'}
    unvalues = {False, True, None, ''}

    for name in names:
        if (v := builtins[name]) not in unvalues:
            # Bind the specific name using default args
            #print("Map[{!r}] -> {}".format(v, name))
            def h(w, obj, suffix="", n=name):
                # first argument "obj" is ignored
                #print("Called handler[{!r}] -> {}".format(obj, n))
                w.emit('interp.getBuiltin("{}")'.format(n), suffix)
            snitliub[id(v)] = h
    return snitliub


class PyObjectTestEmitterEvo4(PyObjectTestEmitter):
    """Class to emit a test PyCode and a JUnit test method for each case.

    The generated code assumes a particular representation for Python in Java,
    which matches the run-time environment explored in Java package evo4.
    """

    def __init__(self, test, writer=None):
        self.writer = PyObjectEmitterEvo3() if writer is None else writer
        self.test = test
        # Compile the lines to byte code
        prog = '\n'.join(test.body)
        code = compile(prog, self.test.name, 'exec')
        self.bytecode = dis.Bytecode(code)
        # Handle built-ins distinctly
        writer.add_special_handlers(PyObjectTestEmitterEvo4.snitliub)

    # Lookup from ids of builtins values to an access expression
    snitliub = _make_handlers()

    def emit_test_method(self, name, c):
        """Emit one JUnit test method with the given name"""
        # Prepare the "before" name space by executing the "case" code
        before = dict()
        exec(c, {}, before)
        # Execute the example code against a copy of that name space
        globals = dict(before)
        exec(self.bytecode.codeobj, globals)
        # Extract those variables names as results to test
        after = {k: globals[k] for k in self.test.test}
        # Check
        #print("before = {!r}".format(before))
        #print("after = {!r}".format(after))
        # Emit the code for the test method
        self.writer.emit_line("@Test")
        self.writer.emit_line("void " + name + "() {")
        with self.writer.indentation():
            self.writer.emit_line("//@formatter:off")
            # Create an interpreter to execute the code object
            self.writer.emit_line(
                "Interpreter interp = Py.createInterpreter();")
            # Load the global name space with the test case values
            self.writer.emit_line("PyDict globals = Py.dict();")
            for k, v in before.items():
                self.writer.emit_line("globals.put(")
                with self.writer.indentation():
                    self.writer.java_string(k, ", ")
                    self.writer.python(v, ");")
            # Execute the code in the Java implementation
            self.writer.emit_line("interp.evalCode(")
            self.writer.emit(self.test.name.upper())
            self.writer.emit(", globals, globals);")
            # Compare named results against the values this Python got
            for k, v in after.items():
                msg = "{} == {}".format(k, repr(v))
                self.writer.emit_line("assertEquals(")
                with self.writer.indentation():
                    self.writer.python(v, ", ")
                    self.writer.emit("globals.get(")
                    self.writer.java_string(k, "), ")
                    self.writer.java_string(msg, ");")
            self.writer.emit_line("//@formatter:on")
        self.writer.emit_line("}")
        return self.writer.emit_line()

