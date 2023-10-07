import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Optional;

public class TestParser {

  public static void main(String[] argv) throws IOException {
    Parser p = new Parser();
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

    String line = in.readLine();
    while (line != null) {
      Optional<Parser.Expression> opt = p.parse(line);
      if (opt.isPresent()) {
        var expr = opt.get();
        System.out.printf("%20s -> %s\n", line, expr.toSexp());
      } else {
        System.out.printf("Can't parse '%s'\n", line);
      }
      line = in.readLine();
    }
  }
}
