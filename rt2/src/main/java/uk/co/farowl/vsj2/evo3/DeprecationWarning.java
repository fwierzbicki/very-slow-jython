package uk.co.farowl.vsj2.evo3;

/** The Python {@code DeprecationWarning} exception. */
class DeprecationWarning extends Warning {

    /** The type of Python object this class implements. */
    static final PyType TYPE =
            new PyType("DeprecationWarning", DeprecationWarning.class);

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param type object being constructed
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected DeprecationWarning(PyType type, String msg,
            Object... args) {
        super(type, msg, args);
    }

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public DeprecationWarning(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
