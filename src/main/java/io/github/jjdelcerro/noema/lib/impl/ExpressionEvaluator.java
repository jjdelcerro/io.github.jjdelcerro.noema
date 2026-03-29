package io.github.jjdelcerro.noema.lib.impl;

import java.util.HashMap;
import java.util.Objects;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;

public class ExpressionEvaluator {

  public static void main(String[] args) {
    // Variables de contexto
    DefaultVars vars = new DefaultVars();
    vars.put("nombre", "Ana");
    vars.put("edad", 17);
    vars.put("saldo", 50.0);
    vars.put("activo", true);

    DefaultFunctions funcs = new DefaultFunctions();
    funcs.put("hola", new Function() {
      @Override
      public Object apply(Object t) {
        return "Hola " + Objects.toString(t);
      }
    });
    funcs.put("get", new Function() {
      @Override
      public Object apply(Object t) {
        return "Hola " + Objects.toString(t);
      }
    });

    String[] pruebas = {
      "edad >= 18", // Boolean
      "nombre == \"Ana\"", // Boolean
      "nombre + \" tiene \" + edad + \" años\"", // String
      "!activo", // Boolean
      "edad > 15 && nombre == \"Ana\"", // Boolean lógico
      "edad >= 18 ? \"Mayor\" : \"Menor\"", // Ternario con String
      "(saldo > 100 || !activo) ? 0 : saldo * 1.1", // Ternario complejo
      "hola(\"pepe\")"
    };

    for (String expr : pruebas) {
      try {
        Object res = eval(expr, vars, funcs);
        System.out.printf("%-50s => %s (%s)%n", expr, res, res != null ? res.getClass().getSimpleName() : "null");
      } catch (Exception e) {
        System.err.println("Error en '" + expr + "': " + e.getMessage());
      }
    }
  }

  public static class DefaultVars extends HashMap<String, Object> implements Vars {

    @Override
    public Object get(String key) {
      return super.get(key);
    }

    @Override
    public boolean containsKey(String key) {
      return super.containsKey(key);
    }
  }

  public static class DefaultFunctions extends HashMap<String, Function> implements Functions {

    @Override
    public Function get(String key) {
      return super.get(key);
    }

    @Override
    public boolean containsKey(String key) {
      return super.containsKey(key);
    }
  }

  public interface Vars {

    Object get(String name);

    public boolean containsKey(String name);
  }

  public interface Functions {

    Function get(String name);

    public boolean containsKey(String name);
  }

  public static Object eval(String str, Vars variables, Functions functions) {
    Evaluator e = new Evaluator(str, variables, functions);
    return e.parse();
  }

  private static class Evaluator {

    private String str;
    private Vars variables;
    private Functions functions;
    private int ch;
    private int pos = -1;

    public Evaluator(String str, Vars variables, Functions functions) {
      this.str = str;
      this.variables = variables;
      this.functions = functions;
    }

    void nextChar() {
      ch = (++pos < str.length()) ? str.charAt(pos) : -1;
    }

    boolean eat(int charToEat) {
      while (ch == ' ') {
        nextChar();
      }
      if (ch == charToEat) {
        nextChar();
        return true;
      }
      return false;
    }

    Object parse() {
      nextChar();
      Object x = parseTernary();
      if (pos < str.length()) {
        throw new RuntimeException("Carácter inesperado: " + (char) ch);
      }
      return x;
    }

    // --- Gramática y Precedencia ---
    // 1. Ternario (?:) - Menor precedencia
    Object parseTernary() {
      Object condition = parseLogicalOr();

      if (eat('?')) {
        Object ifTrue = parseTernary(); // Permite anidación
        if (!eat(':')) {
          throw new RuntimeException("Esperado ':' en operador ternario");
        }
        Object ifFalse = parseTernary();

        // Evaluación perezosa (short-circuiting) básica
        return isTrue(condition) ? ifTrue : ifFalse;
      }
      return condition;
    }

    // 2. OR Lógico (||)
    Object parseLogicalOr() {
      Object left = parseLogicalAnd();
      while (eat('|') && eat('|')) {
        Object right = parseLogicalAnd();
        left = isTrue(left) || isTrue(right); // Short-circuiting manual
      }
      return left;
    }

    // 3. AND Lógico (&&)
    Object parseLogicalAnd() {
      Object left = parseEquality();
      while (eat('&') && eat('&')) {
        Object right = parseEquality();
        left = isTrue(left) && isTrue(right); // Short-circuiting manual
      }
      return left;
    }

    // 4. Igualdad y Comparación (==, !=, >, <, >=, <=)
    Object parseEquality() {
      Object left = parseAdditive();
      for (;;) {
        if (eat('=')) {
          if (!eat('=')) {
            throw new RuntimeException("Operador inválido");
          }
          return equals(left, parseAdditive());
        } else if (eat('!') && eat('=')) {
          return !equals(left, parseAdditive());
        } else if (eat('>')) {
          if (eat('=')) {
            return compare(left, parseAdditive()) >= 0;
          } else {
            return compare(left, parseAdditive()) > 0;
          }
        } else if (eat('<')) {
          if (eat('=')) {
            return compare(left, parseAdditive()) <= 0;
          } else {
            return compare(left, parseAdditive()) < 0;
          }
        } else {
          return left;
        }
      }
    }

    // 5. Adición / Concatenación (+, -)
    Object parseAdditive() {
      Object left = parseMultiplicative();
      for (;;) {
        if (eat('+')) {
          Object right = parseMultiplicative();
          // Si alguno es String, concatenar. Si no, sumar números.
          if (left instanceof String || right instanceof String) {
            left = toString(left) + toString(right);
          } else {
            left = toNumber(left) + toNumber(right);
          }
        } else if (eat('-')) {
          left = toNumber(left) - toNumber(parseMultiplicative());
        } else {
          return left;
        }
      }
    }

    // 6. Multiplicación (*, /)
    Object parseMultiplicative() {
      Object left = parseUnary();
      for (;;) {
        if (eat('*')) {
          left = toNumber(left) * toNumber(parseUnary());
        } else if (eat('/')) {
          left = toNumber(left) / toNumber(parseUnary());
        } else {
          return left;
        }
      }
    }

    // 7. Unario (!, -)
    Object parseUnary() {
      if (eat('-')) {
        return -toNumber(parseUnary());
      }
      if (eat('+')) {
        return parseUnary(); // + unario
      }
      if (eat('!')) {
        return !isTrue(parseUnary());
      }
      return parsePrimary();
    }

    // 8. Primario (Parentesis, Literales, Variables)
    Object parsePrimary() {
      if (eat('(')) {
        Object x = parseTernary();
        if (!eat(')')) {
          throw new RuntimeException("Esperado ')'");
        }
        return x;
      }

      // Números
      if ((ch >= '0' && ch <= '9') || ch == '.') {
        int start = this.pos;
        while ((ch >= '0' && ch <= '9') || ch == '.') {
          nextChar();
        }
        return Double.parseDouble(str.substring(start, this.pos));
      }

      // Booleanos
      if (Character.isJavaIdentifierStart(ch)) {
        int start = this.pos;
        while ( Character.isJavaIdentifierPart(ch) ) {
          nextChar();
        }
        String word = str.substring(start, this.pos);
        switch (StringUtils.lowerCase(word)) {
          case "true":
            return true;
          case "false":
            return false;
          case "null":
            return null;
          default:
            // Variable y funciones
            if (variables != null && variables.containsKey(word)) {
              return variables.get(word);
            }
            if (!eat('(')) {
              throw new RuntimeException("Variable desconocida: " + word);
            }
            Object x = this.parseTernary();
            if (!eat(')')) {
              throw new RuntimeException("Error evaluando el parametro de '"+word+"' cerca de '"+StringUtils.mid(str, pos, 10)+"'");
            }
            Function func = functions.get(word);
            return func.apply(x);
        }
      }

      // Strings (entre comillas dobles)
      if (eat('"')) {
        StringBuilder sb = new StringBuilder();
        while (ch != '"' && ch != -1) {
          sb.append((char) ch);
          nextChar();
        }
        if (!eat('"')) {
          throw new RuntimeException("Cadena no cerrada");
        }
        return sb.toString();
      }

      throw new RuntimeException("Carácter inesperado: " + (char) ch);
    }

    // --- Utilidades de conversión y comparación ---
    private boolean isTrue(Object o) {
      if (o instanceof Boolean) {
        return (Boolean) o;
      }
      // En lógica dinámica, si no es booleano true, consideramos falso (excepto quizás strings no vacíos, aquí seremos estrictos: solo Boolean true)
      return false;
    }

    private double toNumber(Object o) {
      if (o instanceof Number) {
        return ((Number) o).doubleValue();
      }
      throw new RuntimeException("Se esperaba un número pero se obtuvo: " + o);
    }

    private String toString(Object o) {
      return o != null ? o.toString() : "null";
    }

    private boolean equals(Object a, Object b) {
      if (a == null && b == null) {
        return true;
      }
      if (a == null || b == null) {
        return false;
      }
      if (a instanceof String || b instanceof String) {
        return Objects.toString(a, "").compareTo(Objects.toString(b, ""))==0;
      }
      // Comparación laxa para números (ej: 5.0 == 5)
      if (a instanceof Number && b instanceof Number) {
        return toNumber(a) == toNumber(b);
      }
      return a.equals(b);
    }

    private int compare(Object a, Object b) {
      if (a instanceof String || b instanceof String) {
        return Objects.toString(a, "").compareTo(Objects.toString(b, ""));
      }
      return Double.compare(toNumber(a), toNumber(b));
    }

  }

}
