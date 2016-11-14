..  treepython/ref_interp_help.rst


Some Help from the Reference Interpreter
########################################

Orientation
***********

We intend to study how one may construct a Python interpreter in Java.
Our interest is in how to *execute* Python,
not so much how we parse and compile it:
we'll have to tackle that too,
but we'll begin with that central problem of execution.

Incidentally, is it "execution" or "interpretation"?
It is largely a matter of perspective.
In the C implementation of Python,
code is generated for a Python Virtual Machine.
(The design of the machine,
and the machine language it accepts,
change from one version of the implementation to the next.)
From a hardware perspective (or that of the C programmer),
the virtual machine is an interpreter for this intermediate language.

It will get even more layered with a Java implementation.
From the hardware perspective, the JVM itself is an interpreter,
and our Java programs are compiled to its Java bytecode.
We will (probably) want to translate Python into Java bytecode,
as Jython does.
But we might also write an interpreter in Java,
compiled to Java bytecode,
to interpret an intermediate language compiled from Python.

Python possesses another intermediate form,
other than Python bytecode,
that it is useful to study:
this is the AST (abstract syntax tree).
The Python compiler creates the AST as an intermediate product
between your code and Python bytecode,
but it will also provide it to you as an object.
It is attractive to manipulate the AST in tools because it is:

* recognisably related to your source code and its symbols, and
* standardised in a way the bytecode is not.

Otherwise, one would be compelled to start by writing a Python compiler.

Python Compilation Example
**************************

The following is straightforward with a working implementation of Python.
Suppose we have this very simple program::

    x = 41
    y = x + 1
    print(y)

Then we may compile it to an AST like so::

    >>> import ast, dis
    >>> prog = """\
    x = 41
    y = x + 1
    print(y)
    """
    >>> tree = ast.parse(prog)
    >>> ast.dump(tree)
    "Module(body=[Assign(targets=[Name(id='x', ctx=Store())], value=Num(n=41)), 
    Assign(targets=[Name(id='y', ctx=Store())], value=BinOp(left=Name(id='x', c
    tx=Load()), op=Add(), right=Num(n=1))), Expr(value=Call(func=Name(id='print
    ', ctx=Load()), args=[Name(id='y', ctx=Load())], keywords=[]))])"
    >>> 

Now that last line is not very pretty,
so the first thing to put in the tool box
(at ``src/main/python/astutil.py`` relative to the project root)
is a pretty-printer::

    >>> import sys, os
    >>> lib = os.path.join('src', 'main', 'python')
    >>> sys.path.insert(0, lib)
    >>> import astutil
    >>> astutil.pretty(tree)
    Module(
     body=[
       Assign(targets=[Name(id='x', ctx=Store())], value=Num(n=41)),
       Assign(
         targets=[Name(id='y', ctx=Store())],
         value=BinOp(left=Name(id='x', ctx=Load()), op=Add(), right=Num(n=1))),
       Expr(
         value=Call(
           func=Name(id='print', ctx=Load()),
           args=[Name(id='y', ctx=Load())],
           keywords=[]))])
    >>>

The objects in this structure
are officially described in the standard library documentation,
but there is a fuller explanation in `Green Tree Snakes`_.

..  _Green Tree Snakes: https://greentreesnakes.readthedocs.io/en/latest/

We may compile and run (either the source ``prog`` or the AST) to CPython
bytecode like so::

    >>> code = compile(tree, '<prog>', 'exec')
    >>> dis.dis(code)
     2           0 LOAD_CONST               0 (41)
                 3 STORE_NAME               0 (x)

     3           6 LOAD_NAME                0 (x)
                 9 LOAD_CONST               1 (1)
                12 BINARY_ADD
                13 STORE_NAME               1 (y)

     4          16 LOAD_NAME                2 (print)
                19 LOAD_NAME                1 (y)
                22 CALL_FUNCTION            1 (1 positional, 0 keyword pair)
                25 POP_TOP
                26 LOAD_CONST               2 (None)
                29 RETURN_VALUE
    >>> exec(code)
    42
    >>>

We could implement an interpreter in Java for exactly the bytecode Python uses.
However, the bytecode evolves from one release to the next
and is conceived to support the implementation in C.
Moreover,
we probably want to follow Jython's lead and generate Java bytecode ultimately.
So we're taking a different course here for the toy implementation:
we imagine executing the AST directly.
This will get us quickly to some of the implementation questions
central to the interpreter.


