package uk.co.farowl.vsj2.evo4;

/** The Python {@code SystemError} exception. */
class SystemError extends PyException {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("SystemError", SystemError.class));

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param type object being constructed
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected SystemError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    /**
     * Constructor specifying a message.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public SystemError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
