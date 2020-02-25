package uk.co.farowl.vsj2.evo3;

/** The Python {@code Exception} exception. */
class PyException extends BaseException {

    static final PyType TYPE =
            new PyType("Exception", PyException.class);

    protected PyException(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public PyException(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
