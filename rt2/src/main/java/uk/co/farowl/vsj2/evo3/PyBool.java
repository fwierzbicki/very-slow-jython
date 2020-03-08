package uk.co.farowl.vsj2.evo3;

/** The Python {@code bool} object. */
class PyBool extends PyLong {

    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("bool", PyBool.class) //
                    .base(PyLong.TYPE) //
                    .flagNot(PyType.Flag.BASETYPE));

    @Override
    public PyType getType() { return TYPE; }

    // Private so we can guarantee the doubleton. :)
    private PyBool(boolean value) { super(value ? 1 : 0); }

    /** Python {@code False} object. */
    static final PyBool False = new PyBool(false);

    /** Python {@code True} object. */
    static final PyBool True = new PyBool(true);

    @Override
    public String toString() {
        return asSize() == 0 ? "False" : "True";
    }
}
