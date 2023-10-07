# Parser

A demonstration of a hand-written recursive descent parser.

This code does not use a separate tokenizer, as is often the case for
hand-written recursive descent parsers since it's pretty easy to build in the
white-space eating as is done here in the `match` method.

Anyway, this one one way of doing things. It's not the most efficient thing in
the world since it will repeatedly try to match essentially the same thing at
the same position in different methods. One way to rectify that is with what's
called a packrat parser where you keep track of what things you've already
matched at what positions so then if you try to match the same thing at the same
position and you've already tried you can just return the cached result. That
can turn an n-squared (worst case) parse into linear, I believe.

You can read the code in its final state. Also maybe useful is to look at the PR
in this branch and step through the commits one by one to see how the code
evolved over time to add new features. You'll hopefully note that with the basic
framework in place adding new features required not to much code and fairly
localized changes.
