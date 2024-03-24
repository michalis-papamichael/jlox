package lox;

import java.util.List;

interface LoxCollable {
    int arity();
    Object call(Interpreter interpreter, List<Object> arguments);
}
