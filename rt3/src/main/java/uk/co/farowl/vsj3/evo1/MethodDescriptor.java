package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;

import uk.co.farowl.vsj3.evo1.ArgumentError.Mode;

/**
 * Abstract base class for the descriptor of a method defined in Java.
 * This class provides some common behaviour and support methods that
 * would otherwise be duplicated. This is also home to some static
 * methods in support of both sub-classes and other callable objects
 * (e.g. {@link PyJavaMethod}).
 */
abstract class MethodDescriptor extends Descriptor implements FastCall {

    MethodDescriptor(PyType descrtype, PyType objclass, String name) {
        super(descrtype, objclass, name);
    }

    /**
     * Translate a problem with the number and pattern of arguments, in
     * a failed attempt to call the wrapped method, to a Python
     * {@link TypeError}.
     *
     * @param ae expressing the problem
     * @param args positional arguments (only the number will matter)
     * @return a {@code TypeError} to throw
     */
    protected TypeError typeError(ArgumentError ae, Object[] args) {
        int n = args.length;
        switch (ae.mode) {
            case NOARGS:
            case NUMARGS:
            case MINMAXARGS:
                return new TypeError("%s() %s (%d given)", name, ae, n);
            case NOKWARGS:
            default:
                return new TypeError("%s() %s", name, ae);
        }
    }

    /**
     * Check that no positional or keyword arguments are supplied. This
     * is for use when implementing {@code __call__} etc..
     *
     * @param args positional argument array to be checked
     * @param names to be checked
     * @throws ArgumentError if positional arguments are given or
     *     {@code names} is not {@code null} or empty
     */
    final static void checkNoArgs(Object[] args, String[] names)
            throws ArgumentError {
        if (args.length != 0)
            throw new ArgumentError(Mode.NOARGS);
        else if (names != null && names.length != 0)
            throw new ArgumentError(Mode.NOKWARGS);
    }

    /**
     * Check the number of positional arguments and that no keywords are
     * supplied. This is for use when implementing {@code __call__}
     * etc..
     *
     * @param args positional argument tuple to be checked
     * @param expArgs expected number of positional arguments
     * @param names to be checked
     * @throws ArgumentError if the wrong number of positional arguments
     *     are given or {@code kwargs} is not {@code null} or empty
     */
    final static void checkArgs(Object[] args, int expArgs,
            String[] names) throws ArgumentError {
        if (args.length != expArgs)
            throw new ArgumentError(expArgs);
        else if (names != null && names.length != 0)
            throw new ArgumentError(Mode.NOKWARGS);
    }

    /**
     * Check the number of positional arguments and that no keywords are
     * supplied. This is for use when implementing {@code __call__}
     * etc..
     *
     * @param args positional argument tuple to be checked
     * @param minArgs minimum number of positional arguments
     * @param maxArgs maximum number of positional arguments
     * @param names to be checked
     * @throws ArgumentError if the wrong number of positional arguments
     *     are given or {@code kwargs} is not {@code null} or empty
     */
    final static void checkArgs(Object[] args, int minArgs, int maxArgs,
            String[] names) throws ArgumentError {
        int n = args.length;
        if (n < minArgs || n > maxArgs)
            throw new ArgumentError(minArgs, maxArgs);
        else if (names != null && names.length != 0)
            throw new ArgumentError(Mode.NOKWARGS);
    }

}
