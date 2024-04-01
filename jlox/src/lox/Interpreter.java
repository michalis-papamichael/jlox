package lox;

import java.util.ArrayList;
import java.util.List;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void>{

    final Environment globals = new Environment();
    private Environment environment = globals;

    Interpreter(){
        globals.define("clock",new LoxCollable(){
            @Override
            public int arity(){return 0;}

            @Override
            public Object call(Interpreter interpreter,
                               List<Object> arguments){
                return (double)System.currentTimeMillis()/1000.0;
            }

            @Override
            public String toString() {return "<native fn>";}
        });
    }

    void interpret(List<Stmt> statements){
        try {
            for (Stmt statement : statements){
                execute(statement);
            }
        }catch (RuntimeError error){
            Lox.runtimeError(error);
        }
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evalute(expr.left);

        if (expr.operator.type==TokenType.OR){
            if (isTruthy(left)){
                return left;
            }
        }else {
            if (!isTruthy(left)){
                return left;
            }
        }

        return evalute(expr.right);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evalute(expr.expression);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evalute(expr.right);

        switch (expr.operator.type){
            case BANG:{
                return !isTruthy(right);
            }
            case MINUS:{
                checkNumberOperand(expr.operator,right);
                return -(double)right;
            }
        }

        // Unreachable
        return null;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evalute(expr.left);
        Object right = evalute(expr.right);

        switch (expr.operator.type){
            case BANG_EQUAL:{
                return !isEqual(left,right);
            }
            case EQUAL_EQUAL:{
                return isEqual(left,right);
            }
            case GREATER:{
                checkNumberOperands(expr.operator,left,right);
                return (double)left>(double) right;
            }
            case GREATER_EQUAL:{
                checkNumberOperands(expr.operator,left,right);
                return (double)left>=(double) right;
            }
            case LESS:{
                checkNumberOperands(expr.operator,left,right);
                return (double)left<(double) right;
            }
            case LESS_EQUAL:{
                checkNumberOperands(expr.operator,left,right);
                return (double)left<=(double) right;
            }
            case MINUS :{
                checkNumberOperands(expr.operator,left,right);
                return (double)left-(double) right;
            }
            case PLUS:{
                if (left instanceof Double && right instanceof Double){
                    return (double)left+(double) right;
                }
                if (left instanceof String && right instanceof String){
                    return (String)left+(String) right;
                }
//                #Chalenge 7.2
//                if (left instanceof String && right instanceof Double){
//                    return (String)left+String.valueOf((double)right);
//                }
//                if (left instanceof Double && right instanceof String){
//                    return String.valueOf((double)left)+(String)right;
//                }
                throw new RuntimeError(expr.operator,"Operands must be two numbs or two strings");
            }
            case SLASH:{
                checkNumberOperands(expr.operator,left,right);
                return (double)left/(double) right;
            }
            case STAR:{
                checkNumberOperands(expr.operator,left,right);
                return (double)left*(double) right;
            }
        }

        // Unreachable
        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evalute(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument:expr.arguments){
            arguments.add(evalute(argument));
        }

        if (!(callee instanceof LoxCollable)){
            throw new RuntimeError(expr.paren,
                    "Can only call functions and classes.");
        }

        LoxCollable function=(LoxCollable)callee;
        if (arguments.size()!=function.arity()){
            throw  new RuntimeError(expr.paren,"Expected "+
                    function.arity()+" arguments but got "+
                    arguments.size()+".");
        }

        return function.call(this,arguments);
    }

    private Object evalute(Expr expr){
//        NOTE: DONT DELETE FOR NOW - TILL MAKE SURE IT'S NOT THE CORRECT CODE.
//        Object value=expr.accept(this);
//        if (value==null){
//            throw new RuntimeError(((Expr.Variable)expr).name,"Error!");
//        }
//        return value;
        return expr.accept(this);
    }

    private void execute(Stmt stmt){
        stmt.accept(this);
    }

    void executeBlock(List<Stmt> statements, Environment environment){
        Environment previous= this.environment;
        try{
            this.environment=environment;

            for (Stmt statement : statements) {
                execute(statement);
            }
        }finally {
            this.environment=previous;
        }
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements,new Environment(environment));
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evalute(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt,environment);
        environment.define(stmt.name.lexeme,function);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evalute(stmt.condition))){
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evalute(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value!=null){
            value=evalute(stmt.value);
        }
        throw new Return(value);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value=null;
        if (stmt.initializer!=null){
            value=evalute(stmt.initializer);
        }

        environment.define(stmt.name.lexeme,value);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evalute(stmt.condition))){
            execute(stmt.body);
        }
        return null;
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evalute(expr.value);
        environment.assign(expr.name,value);
        return value;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return environment.get(expr.name);
    }

    private boolean isTruthy(Object object){
        if (object==null){
            return false;
        }
        if (object instanceof Boolean){
            return (boolean)object;
        }
        return true;
    }

    private boolean isEqual(Object a, Object b){
        if (a==null && b==null){
            return true;
        }
        if (a==null){
            return false;
        }
        return a.equals(b);
    }

    private void checkNumberOperand(Token operator, Object operand){
        if (operand instanceof Double){
            return;
        }
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator,Object left,Object right){
        if (left instanceof Double && right instanceof Double){
//            #Challenge 7.3
//            if ((double)right==0){
//                throw new RuntimeError(operator,"Denominator should not be zero");
//            }
            return;
        }
        throw new RuntimeError(operator,"Operands must be numbers");
    }

    private String stringify(Object object){
        if (object==null){
            return "nil";
        }
        if (object instanceof Double){
            String text = object.toString();
            if (text.endsWith(".0")){
                text=text.substring(0,text.length()-2);
            }
            return text;
        }
        return object.toString();
    }
}
