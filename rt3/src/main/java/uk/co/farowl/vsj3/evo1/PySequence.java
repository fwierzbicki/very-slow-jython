package uk.co.farowl.vsj3.evo1;

/**
 * Sequences exhibit certain common behaviours and utilities that
 * implement them need to call back into the object by this interface.
 * This interface cannot be used as a marker for objects that implement
 * the sequence protocol because it is perfectly possible for a
 * user-defined Python type to do so without its Java implementation
 * implementing {@code PySequence}. A proxy class could be created that
 * holds such an object and does implement {@code PySequence}.
 */
interface PySequence extends CraftedType {

    // /** Get one element from the sequence. */
    // PyObject getItem(int i);

    // /** Set one element from the sequence (if mutable). */
    // void setItem(int i, PyObject v);

    // /**
    // * Return a sequence of the target type, concatenating this with a
    // * sequence of possibly different type.
    // */
    // <S extends PySequence> S concat(PyObject other);

    /**
     * Return a sequence of the target type, by repeatedly concatenating
     * {@code n} copies of the present value.
     *
     * @param n number of repeats
     * @return repeating sequence
     */
    // <S extends PySequence> S repeat(int n);
    PySequence repeat(int n);
}
