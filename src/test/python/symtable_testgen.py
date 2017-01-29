# Generate test cases of nested functions for exploring the scope of names

import sys, ast, types, symtable
from dis import dis, show_code, disassemble
from pprint import pprint
import astutil
from symbolutil import (show_symbol_table, show_module, list_symbol_tables,
                    to_scope_name)


# Create the body (one or two statements) of one block
def one_body(i, value=42):
    g = "global x"
    n = "nonlocal x"
    a = "x = {}".format(value)
    u = "y = x+1"
    p = "pass"
    if i==0:
        return (p,)
    elif i==1:
        return (u,)
    elif i==2:
        return (a,)
    elif i==3:
        return (g, a)
    else:
        return (n, a)

# Generate programs with 3 levels of function
def emit_java_3_level_tests():
    count = 0
    for i in range(4): # nonlocal meaningless at first two levels
        for j in range(4):
            for k in range(1,5): # all cases but empty meaningful
                prog, name = one_prog(i, j, k)
                if emit_java_symtable_test(prog, name):
                    count += 1
    return count

# Generate programs with 3 levels of function to test skip-reference
def emit_java_4_level_tests():
    # test nonlocal "skipping"
    count = 0
    # Carefully minimise number of cases
    for i in (1,2):
        for j in (1,2): # intended referent
            for k in (0,3): # not mentioned or explicit global
                for l in (1,4): # level 4 reference
                    prog, name = one_prog(i, j, k, l)
                    if emit_java_symtable_test(prog, name):
                        count += 1
    return count

# Generate programs with 3 or 4 levels of function
def emit_java_all_progs():
    # Carefully minimise number of cases
    count = emit_java_3_level_tests()
    count += emit_java_4_level_tests()
    return count

# Create one "program" of a module and up to 4 nested function blocks
def one_prog(i, j, k, l=0, make_calls=None):
    lines = []
    tabs = 0
    def indent(s): return "    "*tabs + s

    lines.append("def f():")
    tabs += 1

    lines.append(indent("def g():"))
    tabs += 1

    if l > 0:
        lines.append(indent("def h():"))
        tabs += 1
        lines += list(map(indent, one_body(l, 42000)))
        tabs -= 1
        if make_calls=='early': lines.append(indent("h()"))

    lines += list(map(indent, one_body(k, 4200)))
    if l>0 and make_calls=='late': lines.append(indent("h()"))

    tabs -= 1
    if make_calls=='early': lines.append(indent("g()"))
    lines += list(map(indent, one_body(j, 420)))
    if make_calls=='late': lines.append(indent("g()"))

    tabs -= 1
    if make_calls=='early': lines.append(indent("f()"))
    lines += list(map(indent, one_body(i)))
    if make_calls=='late': lines.append(indent("f()"))

    return "\n".join(lines), "example{}{}{}{}".format(i,j,k,l)

# Emit the generated program and related Java tree Python (if it compiles)
def emit_java_symtable_test(prog, name):
    #print("\n# {}\n{}".format(name, prog))
    tree = ast.parse(prog, "m")
    try:
        c = compile(tree, name, 'exec')
    except SyntaxError as se:
        #print("INVALID:", name, se)
        return False

    # Get the symbol tables
    mst = symtable.symtable(prog, "<module>", 'exec')
    stlist = list_symbol_tables(mst)

    # Generate test for Java
    print("    @Test public void {}() {{".format(name))
    print("        // @formatter:off")
    emit_java_ast(prog, tree)
    print("        // @formatter:on")
    print()
    print("checkExample(module, new RefSymbol[][]{")
    for st in stlist:
        emit_java_symbol_table(st)
    print("});")
    print("}\n")

    return True

def emit_java_symbol_table(st):
    print("    { //", st)
    for s in st.get_symbols():
        emit_java_refsymbol(s)
    print("    },")

def emit_java_refsymbol(s):
    """ Emit the properties of one symbol as a Java RefSymbol."""
    def b(fn):
        return repr(bool(fn())).lower()
    scope = 'SymbolTable.ScopeType.' + to_scope_name(s._Symbol__scope)
    props = (s.is_assigned, s.is_declared_global, s.is_free, s.is_global,
        s.is_imported, s.is_local, s.is_namespace, s.is_parameter,
        s.is_referenced)
    bools = ', '.join(map(b, props))
    print('        new RefSymbol("{}", {}, {}),'.format(
        s.get_name(), scope, bools))


def emit_java_ast(prog, tree):
    """ Emit the AST in Java functional form. """
    for line in prog.splitlines():
        print("        // {}".format(line))
    print("        mod module", end=" = ")
    astutil.pretty_java(tree, width=120)
    print("        ;")

class_prog = """\
class A:
    x = 42
    def g():
        print(x)
x = 6
"""

func_prog = class_prog.replace("class A", "def f()", 1)


def run_prog(prog):
    # Attempt to run prog and dump the globals (w/o built-ins)
    gbl = {}
    exec(prog, gbl)
    del gbl['__builtins__']
    pprint(gbl)

def show_example(i,j,k,l):
    # Generate the example
    prog, name = one_prog(i,j,k,l)
    print(prog)
    show_prog(prog, name)

_ONE_TEST = False

if __name__ == "__main__" :

    if _ONE_TEST:
    # Just do one test and print
        prog, name = one_prog(2,2,0,4)
        print(prog)
        show_module(prog)
        run_prog(prog)
    else:
        # Systematic example generator
        count = emit_java_all_progs()
        print(count, "programs", file=sys.stderr)
    
