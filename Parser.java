import java.util.Optional;
import java.io.Console;

public class Parser {

  public static void main(String[] argv) {
    Console c = System.console();
    Parser p = new Parser();

    while (true) {
      String formula = c.readLine("> ");
      Optional<Expression> opt = p.parse(formula);
      if (opt.isPresent()) {
        System.out.println(opt.get());
        System.out.printf("At 100: %f\n", opt.get().evaluateAt(100));
      } else {
        System.out.printf("Can't parse '%s'\n", formula);
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Expressions

  public interface Expression {
    public double evaluateAt(double x);
  }

  public record Number(double value) implements Expression {
    public double evaluateAt(double x) {
      return value;
    }
  }

  public record Variable(String name) implements Expression {
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
  }

  public record Addition(Expression left, Expression right) implements Expression {
    public double evaluateAt(double x) {
      return left.evaluateAt(x) + right.evaluateAt(x);
    }
  }
  public record Subtraction(Expression left, Expression right) implements Expression {
    public double evaluateAt(double x) {
      return left.evaluateAt(x) - right.evaluateAt(x);
    }
  }
  public record Multiplication(Expression left, Expression right) implements Expression {
    public double evaluateAt(double x) {
      return left.evaluateAt(x) * right.evaluateAt(x);
    }
  }
  public record Division(Expression left, Expression right) implements Expression {
    public double evaluateAt(double x) {
      return left.evaluateAt(x) / right.evaluateAt(x);
    }
  }
  public record Exponentiation(Expression left, Expression right) implements Expression {
    public double evaluateAt(double x) {
      return Math.pow(left.evaluateAt(x), right.evaluateAt(x));
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Parsing

  private record PartialParse(Expression expression, int position) {}


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

  private PartialParse fail() {
    return null;
  }

  private PartialParse expression(String s, int pos) {
    var pp = addition(s, pos);
    if (pp != null) return pp;

    pp = subtraction(s, pos);
    if (pp != null) return pp;

    pp = term(s, pos);
    if (pp != null) return pp;

    return fail();
  }

  public PartialParse term(String s, int pos) {
    var pp = multiplication(s, pos);
    if (pp != null) return pp;

    pp = division(s, pos);
    if (pp != null) return pp;

    pp = factor(s, pos);
    if (pp != null) return pp;

    return fail();
  }

  public PartialParse factor(String s, int pos) {
    var pp = exponentiation(s, pos);
    if (pp != null) return pp;

    pp = atomic(s, pos);
    if (pp != null) return pp;

    return fail();
  }

  public PartialParse atomic(String s, int pos) {
    var pp = number(s, pos);
    if (pp != null) return pp;

    pp = variable(s, pos);
    if (pp != null) return pp;

    pp = parenthesized(s, pos);
    if (pp != null) return pp;

    return fail();
  }

  public PartialParse parenthesized(String s, int pos) {
    var lparen = match("(", s, pos);
    if (lparen != null) {
      var expr = expression(s, lparen.position());
      if (expr != null) {
        var rparen = match(")", s, expr.position());
        if (rparen != null) {
          return ok(expr.expression(), rparen.position());
        }
      }
    }
    return fail();
  }

  private PartialParse number(String s, int pos) {
    int start = pos;
    while (pos < s.length() && Character.isDigit(s.codePointAt(pos))) {
      pos++;
    }
    if (pos > start) {
      var pp = match(".", s, pos);
      if (pp != null) {
        pos = pp.position();
        while (pos < s.length() && Character.isDigit(s.codePointAt(pos))) {
          pos++;
        }
      }
      return ok(new Number(Double.valueOf(s.substring(start, pos))), pos);
    } else {
      return fail();
    }
  }

  private PartialParse variable(String s, int pos) {
    int start = pos;
    while (pos < s.length() && Character.isLetter(s.codePointAt(pos))){
      pos += Character.charCount(s.codePointAt(pos));
    }
    if (pos > start) {
      return ok(new Variable(s.substring(start, pos)), pos);
    } else {
      return fail();
    }
  }

  private PartialParse addition(String s, int pos) {
    var left = term(s, pos);
    if (left != null) {
      var op = match("+", s, left.position());
      if (op != null) {
        var right = term(s, op.position());
        if (right != null) {
          return ok(new Addition(left.expression(), right.expression()), right.position());
        }
      }
    }
    return fail();
  }

  private PartialParse subtraction(String s, int pos) {
    var left = term(s, pos);
    if (left != null) {
      var op = match("-", s, left.position());
      if (op != null) {
        var right = term(s, op.position());
        if (right != null) {
          return ok(new Subtraction(left.expression(), right.expression()), right.position());
        }
      }
    }
    return fail();
  }

  private PartialParse multiplication(String s, int pos) {
    var left = factor(s, pos);
    if (left != null) {
      var op = match("*", s, left.position());
      if (op != null) {
        var right = factor(s, op.position());
        if (right != null) {
          return ok(new Multiplication(left.expression(), right.expression()), right.position());
        }
      }
    }
    return fail();
  }

  private PartialParse division(String s, int pos) {
    var left = factor(s, pos);
    if (left != null) {
      var op = match("/", s, left.position());
      if (op != null) {
        var right = factor(s, op.position());
        if (right != null) {
          return ok(new Division(left.expression(), right.expression()), right.position());
        }
      }
    }
    return fail();
  }

  private PartialParse exponentiation(String s, int pos) {
    var base = atomic(s, pos);
    if (base != null) {
      var op = match("^", s, base.position());
      if (op != null) {
        var exp = atomic(s, op.position());
        if (exp != null) {
          return ok(new Exponentiation(base.expression(), exp.expression()), exp.position());
        }
      }
    }
    return fail();
  }



  private PartialParse match(String what, String s, int pos) {
    int newPos = ws(s, pos);
    if (s.indexOf(what, newPos) == newPos) {
      return ok(null, ws(s, newPos + what.length()));
    }
    return fail();
  }

  private int ws(String s, int pos) {
    while (pos < s.length() && Character.isWhitespace(s.codePointAt(pos))){
      pos += Character.charCount(s.codePointAt(pos));
    }
    return pos;
  }



}
