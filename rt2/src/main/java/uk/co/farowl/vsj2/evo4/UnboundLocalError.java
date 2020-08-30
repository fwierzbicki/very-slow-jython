package uk.co.farowl.vsj2.evo4;

/** The Python {@code UnboundLocalError} exception. */
class UnboundLocalError extends NameError {

    static final PyType TYPE =
            PyType.fromSpec(new PyType.Spec("UnboundLocalError",
                    UnboundLocalError.class).base(NameError.TYPE));

    protected UnboundLocalError(PyType type, String msg,
            Object... args) {
        super(type, msg, args);
    }

    public UnboundLocalError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
