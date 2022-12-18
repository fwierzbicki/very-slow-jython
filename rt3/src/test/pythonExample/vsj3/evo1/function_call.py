# function_call.py
#
# We define functions with various signatures and call them.

def fdef(a, b):
    return (a, b)

flam = lambda a, b : (a, b)

def g(a, b, c=93, d=94, *args, r, s=97, t=98, **kwargs):
    # We don't have str.format at the time of writing
    def fmt(text, value):
        return text + '=' + value.__repr__()
    parts = [
        fmt('g: a', a),
        fmt('b', b),
        fmt('c', c),
        fmt('d', d),
        fmt('args', args),
        fmt('r', r),
        fmt('s', s),
        fmt('t', t),
        fmt('kwargs', kwargs),
    ]
    return ", ".join(parts)

def h(*args, **kwargs):
    # We don't have str.format at the time of writing
    def fmt(text, value):
        return text + '=' + value.__repr__()
    parts = [
        fmt('h: args', args),
        fmt('kwargs', kwargs),
    ]
    return ", ".join(parts)

def f1(a, b, u=1):
    z = 1
    a = (a + b) * u
    def f2(c = -1, v = 2):
        nonlocal z
        y = 11 + z
        z = v
        def f3(d, e):
            nonlocal y, z
            w = 111
            x = w + y + z
            y = 3 + w
            z = 4
            return [a, b, c, d, e, x, y, z]
        return f3
    return f2()


fdef_r = fdef(11, 12)
flam_r = flam(21, 22)

### Calls to complex function g to test argument handling

# Minimal
g_r1 = g(31, 32, r=33)
    # a=31, b=32, c=93, d=94, args=(), r=33, s=97, t=98, kwargs={}

# Override a positional default
g_r2 = g(31, 32, 34, r=35)
    # a=31, b=32, c=34, d=94, args=(), r=35, s=97, t=98, kwargs={}

# With overflow into *args
g_r3 = g(31, 32, 34, 35, 36, 37, r=38)
    # a=31, b=32, c=34, d=35, args=(36, 37), r=38, s=97, t=98, kwargs={}

# Override a keyword default
g_r4 = g(31, 32, r=34, t=35)
    # a=31, b=32, c=93, d=94, args=(), r=34, s=97, t=35, kwargs={}

# With overflow into **kwargs
g_r5 = g(31, 32, r=34, t=35, y=36, x=37)
    # a=31, b=32, c=93, d=94, args=(), r=34, s=97, t=35,
    #     kwargs={'y': 36, 'x': 37}


### Calls to function h with classic tuple and dict arguments

h_r1 = h()
    # args=(), kwargs={}
h_r2 = h(1)
    # args=(1,), kwargs={}
h_r3 = h(a=10)
    # args=(), kwargs={'a': 10}
h_r4 = h(1, 2, 3, a=11, b=12, c=13)
    # args=(1, 2, 3), kwargs={'a': 11, 'b': 12, 'c': 13}
h_r5 = h(*(1, 2, 3), **{'a': 11, 'b': 12, 'c': 13})
    # args=(1, 2, 3), kwargs={'a': 11, 'b': 12, 'c': 13}



### Tests involving f1, f2, f3 to test closure handling.



# Delete since function object not marshallable:
del fdef, flam, g, h, f1

