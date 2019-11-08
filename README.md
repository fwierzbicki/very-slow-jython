# The Very Slow Jython Project

The aim of the Very Slow Jython project is to re-think implementation choices in the [Jython](http://www.jython.org) core, through the gradual, narrated evolution of a toy implementation,
starting from zero code.

This project is as much a writing project as a programming one.
The narrative of the development is in reStructuredText using Sphinx.

## Contents

Without building the project, GitHub does a reasonable job of rendering it, except for the [contents](src/site/sphinx/index.rst).
The main sections are these:

1. [Background to the Very Slow Jython Project](docs/src/site/sphinx/background/background.rst)
1. [A Tree-Python Interpreter](docs/src/site/sphinx/treepython/treepython.rst)
   1. [Some Help from the Reference Interpreter](docs/src/site/sphinx/treepython/ref_interp_help.rst)
   2. [An Interpreter in Java for the Python AST](docs/src/site/sphinx/treepython/ast_java.rst)
   3. [Type and Operation Dispatch](docs/src/site/sphinx/treepython/type+dispatch.rst)
   4. [Interpretation of Simple Statements](docs/src/site/sphinx/treepython/simple_statements.rst) 
1. [A Generated Code Interpreter](docs/src/site/sphinx/generated-code/generated-code.rst)
   1. [The Data Model](docs/src/site/sphinx/generated-code/data-model.rst)
   2. [The `frame` as an Interpreter](docs/src/site/sphinx/generated-code/frame-as-interpreter.rst)
1. [Architecture](docs/src/site/sphinx/architecture/architecture.rst)
   1. [Interpreter Structure](docs/src/site/sphinx/architecture/interpreter-structure.rst)

## Building the Project

This project builds using Gradle.
As it stands, the primary output is the narrative.
It uses the excellent [sphinx-gradle-plugin](https://trustin.github.io/sphinx-gradle-plugin),
which delivers HTML output to the directory ``docs/build/site``.

### Windows
To build the narrative documentation type:

    .\gradlew --console=plain site

The conventional main task:

    .\gradlew --console=plain build

compiles and runs the unit tests that are the examples in the text.


## Organisation of Sources

The [narrative](docs/src/site/sphinx) of the Very Slow Jython project
will be maintained as documentation in reStructuredText,
in the same repository as the code itself.
However the "false starts", various failed implementation ideas,
will stay around so that the narrative can make reference to them.

Currently, I'm not sure how I want to organise the successive versions:
probably they will exist as versions, concurrently in the package structure,
at the cost of some duplication.

