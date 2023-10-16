import java.io.Console;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleFunction;

/**
 * A parser and evaluator for arithmetic expressions. Uses a hand-written
 * recursive descent parser and can parse +, -, *, /, %, and ^ with proper
 * precedence plus ()s, a few mathematical constants, a variable x, and a
 * handful of function calls. Oh and a factorial operator, !.
 *
 * In addition to demonstrating this parsing technique, this code also uses
 * records which are another kind of syntatic sugar for making classes with
 * immutable values.
 *
 * The main method provides a simple REPL where you can type a formula and it
 * will print out the tree (using the fact that record classes have a pretty
 * useful toString method) and also evaluates the formula at x=100. Obviously to
 * actually use this code in some more interesting context you wouldn't use this
 * main method but it's reasonable for interactive testing.
 */
public class Parser {

  public static void main(String[] argv) {
    Console c = System.console();
    Parser p = new Parser();

    while (true) {
      String formula = c.readLine("> ");
      if (formula == null) {
        System.out.println("\nBye.");
        break;
      } else {
        Optional<Expression> opt = p.parse(formula);
        if (opt.isPresent()) {
          System.out.println(opt.get());
          System.out.printf("At 100: %f\n", opt.get().evaluateAt(100));
        } else {
          System.out.printf("Can't parse '%s'\n", formula);
        }
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Functions - define named functions by adding them to this map.

  private Map<String, DoubleFunction<Double>> functions = Map.of(
    "sqrt",
    x -> Math.sqrt(x),
    "ln",
    x -> Math.log(x)
  );

  // Implementation for ! operator.
  private double factorial(double n) {
    if (n < 1) {
      return 1;
    } else {
      return n * factorial(n - 1);
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Expressions

  public static interface Expression {
    public double evaluateAt(double x);

    public String toSexp();

    default String sexp(String name, Expression arg) {
      return "(" + name + " " + arg.toSexp() + ")";
    }

    default String sexp(String name, Expression a, Expression b) {
      return "(" + name + " " + a.toSexp() + " " + b.toSexp() + ")";
    }
  }

  public static record Number(double value) implements Expression {
    public double evaluateAt(double x) {
      return value;
    }

    public String toSexp() {
      if (value % 1 == 0) {
        return String.valueOf((long) value);
      } else {
        return String.valueOf(value);
      }
    }
  }

  public static record Variable(String name) implements Expression {
    public double evaluateAt(double x) {
      switch (name.toLowerCase()) {
        case "x":
          return x;
        case "pi":
        case "π":
          return Math.PI;
        case "tau":
        case "𝜏":
          return Math.TAU;
        case "e":
          return Math.E;
        default:
          throw new RuntimeException("No binding for " + name);
      }
    }

    public String toSexp() {
      return name;
    }
  }

  public static record UnaryFunction(
    String name,
    DoubleFunction<Double> fn,
    Expression arg
  )
    implements Expression {
    public double evaluateAt(double x) {
      return fn.apply(arg.evaluateAt(x));
    }

    public String toSexp() {
      return sexp(name, arg);
    }
  }

  public static record Addition(Expression left, Expression right)
    implements Expression {
    public double evaluateAt(double x) {
      return left.evaluateAt(x) + right.evaluateAt(x);
    }

    public String toSexp() {
      return sexp("+", left, right);
    }
  }

  public static record Subtraction(Expression left, Expression right)
    implements Expression {
    public double evaluateAt(double x) {
      return left.evaluateAt(x) - right.evaluateAt(x);
    }

    public String toSexp() {
      return sexp("-", left, right);
    }
  }

  public static record Multiplication(Expression left, Expression right)
    implements Expression {
    public double evaluateAt(double x) {
      return left.evaluateAt(x) * right.evaluateAt(x);
    }

    public String toSexp() {
      return sexp("*", left, right);
    }
  }

  public static record Division(Expression left, Expression right)
    implements Expression {
    public double evaluateAt(double x) {
      return left.evaluateAt(x) / right.evaluateAt(x);
    }

    public String toSexp() {
      return sexp("/", left, right);
    }
  }

  public static record Remainder(Expression left, Expression right)
    implements Expression {
    public double evaluateAt(double x) {
      return left.evaluateAt(x) % right.evaluateAt(x);
    }

    public String toSexp() {
      return sexp("%", left, right);
    }
  }

  public static record Exponentiation(Expression left, Expression right)
    implements Expression {
    public double evaluateAt(double x) {
      return Math.pow(left.evaluateAt(x), right.evaluateAt(x));
    }

    public String toSexp() {
      return sexp("^", left, right);
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Parsing

  private record PartialParse(Expression expression, int position) {}

  private record Token(String text, int position) {}

  public Optional<Expression> parse(String s) {
    var pp = expression(s, 0);
    if (pp != null && pp.position() == s.length()) {
      return Optional.of(pp.expression());
    } else {
      return Optional.empty();
    }
  }

  private PartialParse ok(Expression expr, int pos) {
    return new PartialParse(expr, pos);
  }

  private PartialParse expression(String s, int pos) {
    var pp = additive(s, pos);
    if (pp != null) return pp;

    pp = term(s, pos);
    if (pp != null) return pp;

    return null;
  }

  public PartialParse term(String s, int pos) {
    var pp = multiplicative(s, pos);
    if (pp != null) return pp;

    pp = factor(s, pos);
    if (pp != null) return pp;

    return null;
  }

  public PartialParse factor(String s, int pos) {
    var pp = exponentiation(s, pos);
    if (pp != null) return pp;

    pp = factorialOrAtomic(s, pos);
    if (pp != null) return pp;

    return null;
  }

  public PartialParse factorialOrAtomic(String s, int pos) {
    var pp = atomic(s, pos);
    if (pp != null) {
      var bang = match(s, pp.position(), "!");
      if (bang != null) {
        return ok(
          new UnaryFunction("!", this::factorial, pp.expression()),
          bang.position()
        );
      } else {
        return pp;
      }
    }
    return null;
  }

  public PartialParse atomic(String s, int pos) {
    // Have to match function call before variable since they start the same
    // way. Could combine them into one matching function
    var pp = functionCall(s, pos);
    if (pp != null) return pp;

    pp = number(s, pos);
    if (pp != null) return pp;

    pp = variable(s, pos);
    if (pp != null) return pp;

    pp = parenthesized(s, pos);
    if (pp != null) return pp;

    return null;
  }

  public PartialParse functionCall(String s, int pos) {
    var n = name(s, pos);
    if (n != null) {
      var arg = parenthesized(s, n.position());
      if (arg != null) {
        return ok(
          new UnaryFunction(
            n.text(),
            functions.get(n.text()),
            arg.expression()
          ),
          arg.position()
        );
      }
    }
    return null;
  }

  public PartialParse parenthesized(String s, int pos) {
    var lparen = match(s, pos, "(");
    if (lparen != null) {
      var expr = expression(s, lparen.position());
      if (expr != null) {
        var rparen = match(s, expr.position(), ")");
        if (rparen != null) {
          return ok(expr.expression(), rparen.position());
        }
      }
    }
    return null;
  }

  private PartialParse number(String s, int pos) {
    int start = pos;
    while (pos < s.length() && Character.isDigit(s.codePointAt(pos))) {
      pos++;
    }
    if (pos > start) {
      if (lookingAt(s, pos, ".")) {
        pos++;
        while (pos < s.length() && Character.isDigit(s.codePointAt(pos))) {
          pos++;
        }
      }
      return ok(new Number(Double.valueOf(s.substring(start, pos))), pos);
    } else {
      return null;
    }
  }

  private PartialParse variable(String s, int pos) {
    var n = name(s, pos);
    if (n != null) {
      return ok(new Variable(n.text()), n.position());
    } else {
      return null;
    }
  }

  private PartialParse additive(String s, int pos) {
    var left = term(s, pos);
    if (left != null) {
      var op = match(s, left.position(), "+", "-");
      if (op != null) {
        var right = expression(s, op.position());
        if (right != null) {
          switch (op.text()) {
            case "+":
              return ok(
                new Addition(left.expression(), right.expression()),
                right.position()
              );
            case "-":
              return ok(
                new Subtraction(left.expression(), right.expression()),
                right.position()
              );
          }
        }
      }
    }
    return null;
  }

  private PartialParse multiplicative(String s, int pos) {
    var left = factor(s, pos);
    if (left != null) {
      var op = match(s, left.position(), "*", "/", "%");
      if (op != null) {
        var right = term(s, op.position());
        if (right != null) {
          switch (op.text()) {
            case "*":
              return ok(
                new Multiplication(left.expression(), right.expression()),
                right.position()
              );
            case "/":
              return ok(
                new Division(left.expression(), right.expression()),
                right.position()
              );
            case "%":
              return ok(
                new Remainder(left.expression(), right.expression()),
                right.position()
              );
          }
        }
      }
    }
    return null;
  }

  private PartialParse exponentiation(String s, int pos) {
    var base = factorialOrAtomic(s, pos);
    if (base != null) {
      var op = match(s, base.position(), "^");
      if (op != null) {
        var exp = factor(s, op.position());
        if (exp != null) {
          return ok(
            new Exponentiation(base.expression(), exp.expression()),
            exp.position()
          );
        }
      }
    }
    return null;
  }

  private Token match(String s, int pos, String... whats) {
    var newPos = ws(s, pos);
    for (var what : whats) {
      if (lookingAt(s, newPos, what)) {
        var end = newPos + what.length();
        return new Token(s.substring(newPos, end), ws(s, end));
      }
    }
    return null;
  }

  private boolean lookingAt(String s, int pos, String what) {
    return pos < s.length() && s.indexOf(what, pos) == pos;
  }

  private Token name(String s, int pos) {
    int start = ws(s, pos);
    while (pos < s.length() && Character.isLetter(s.codePointAt(pos))) {
      pos += Character.charCount(s.codePointAt(pos));
    }
    if (pos > start) {
      return new Token(s.substring(start, pos), ws(s, pos));
    }
    return null;
  }

  // Eat whitespace and return the new position. Used in match to allow
  // whitespace around punctuation.
  private int ws(String s, int pos) {
    while (pos < s.length() && Character.isWhitespace(s.codePointAt(pos))) {
      pos += Character.charCount(s.codePointAt(pos));
    }
    return pos;
  }
}
