# Beermat's bridge to SymPy (docs/symbolic.md#bundled-sympy). One request per line on stdin, one
# answer per line on stdout, both JSON. Expressions travel as trees (lists), never as code, so
# nothing from a sheet is ever evaluated as Python. Names travel as numbers: the app keeps the
# table, so any name (`θ*`, `M_max`, `\bf F`) arrives as the symbol v0, v1, ...
#
# Request:  {"expr": tree, "keywords": [["simplify"], ["solve", tree], ...], "words": {"3": "real", ...}}
# ("words": the plain names, for keywords that take a word, as assume(x, real))
# Answer:   {"ok": tree} or {"error": "message"}
#
# Trees: ["q", "p", "q"] a rational; ["r", "1.5"] a decimal; ["v", k] the k-th name; ["s", "C1"] a
# name SymPy made; ["pi"], ["e"], ["I"], ["oo"]; ["+", a, b, ...]; ["*", a, b, ...]; ["^", a, b];
# ["==", a, b] (and "!=", "<", "<=", ">", ">="); ["f", "sin", a, ...] a known function;
# ["u", k, a, ...] the k-th name used as a function; ["m", rows, cols, a, ...] a matrix, by rows;
# ["str", "+"] a string.

import json
import os
import sys

# The user's own packages (docs/symbolic.md#the-users-own-packages), after the bundled ones, one
# folder per Python minor version. -I leaves the user's site out, so the folder is added here.
if os.environ.get("BEERMAT_PACKAGES"):
    sys.path.append(os.path.join(os.environ["BEERMAT_PACKAGES"], "%d.%d" % sys.version_info[:2], "site-packages"))

import sympy as sp

names = {}


def var(k):
    if k not in names:
        names[k] = sp.Symbol("v%d" % k)
    return names[k]


RELATIONS = {"==": sp.Eq, "!=": sp.Ne, "<": sp.Lt, "<=": sp.Le, ">": sp.Gt, ">=": sp.Ge}

FUNCTIONS = {
    "sin": sp.sin, "cos": sp.cos, "tan": sp.tan, "cot": sp.cot, "sec": sp.sec, "csc": sp.csc,
    "asin": sp.asin, "acos": sp.acos, "atan": sp.atan, "acot": sp.acot,
    "sinh": sp.sinh, "cosh": sp.cosh, "tanh": sp.tanh, "coth": sp.coth,
    "asinh": sp.asinh, "acosh": sp.acosh, "atanh": sp.atanh,
    "exp": sp.exp, "ln": sp.log, "log": sp.log, "sqrt": sp.sqrt, "abs": sp.Abs,
    "sign": sp.sign, "floor": sp.floor, "ceil": sp.ceiling, "Re": sp.re, "Im": sp.im,
    "atan2": lambda y, x: sp.atan2(y, x), "gamma": sp.gamma, "erf": sp.erf, "erfc": sp.erfc,
    "Heaviside": sp.Heaviside, "Φ": sp.Heaviside, "Dirac": sp.DiracDelta, "δ": sp.DiracDelta,
    "nthroot": lambda x, n: sp.root(x, n),
    "max": sp.Max, "min": sp.Min, "factorial": sp.factorial,
}


def build(t):
    tag = t[0]
    if tag == "q":
        return sp.Rational(int(t[1]), int(t[2]))
    if tag == "r":
        return sp.Float(t[1])
    if tag == "v":
        return var(t[1])
    if tag == "pi":
        return sp.pi
    if tag == "e":
        return sp.E
    if tag == "I":
        return sp.I
    if tag == "oo":
        return sp.oo
    if tag == "+":
        return sp.Add(*[build(a) for a in t[1:]])
    if tag == "*":
        return sp.Mul(*[build(a) for a in t[1:]])
    if tag == "^":
        return sp.Pow(build(t[1]), build(t[2]))
    if tag in RELATIONS:
        return RELATIONS[tag](build(t[1]), build(t[2]))
    if tag == "and":
        return sp.And(*[build(a) for a in t[1:]])
    if tag == "or":
        return sp.Or(*[build(a) for a in t[1:]])
    if tag == "m":
        rows, cols = t[1], t[2]
        return sp.Matrix(rows, cols, [build(a) for a in t[3:]])
    if tag == "u":
        return sp.Function("F%d" % t[1])(*[build(a) for a in t[2:]])
    if tag == "f":
        return function(t[1], t[2:])
    raise ValueError("unknown tree " + tag)


def function(name, args):
    if name == "int":
        f, x = build(args[0]), build(args[1])
        if len(args) == 4:
            return sp.integrate(f, (x, build(args[2]), build(args[3])))
        return sp.integrate(f, x)
    if name == "diff":
        n = build(args[2]) if len(args) > 2 else 1
        return sp.diff(build(args[0]), build(args[1]), n)
    if name == "lim":
        direction = "+-"
        if len(args) > 3:
            direction = args[3][1]
        return sp.limit(build(args[0]), build(args[1]), build(args[2]), dir=direction)
    if name in ("sum", "prod"):
        f, i, a, b = [build(a) for a in args]
        return (sp.summation if name == "sum" else sp.product)(f, (i, a, b))
    if name in FUNCTIONS:
        return FUNCTIONS[name](*[build(a) for a in args])
    raise ValueError("%s can't be evaluated symbolically" % name)


# Keywords (docs/symbolic.md). Each takes the expression so far and its arguments as trees.

def symbol(t, what):
    s = build(t)
    if not isinstance(s, sp.Symbol):
        raise ValueError("%s needs a variable" % what)
    return s


def variables(args, what):
    out = []
    for a in args:
        if a[0] == "m":
            out += [symbol(e, what) for e in a[3:]]
        else:
            out.append(symbol(a, what))
    return out


def equations(e):
    """A matrix of equations is a system; `a == b` and an expression (= 0) are one equation."""
    if isinstance(e, sp.MatrixBase):
        return list(e)
    return [e]


def solve(e, args):
    xs = variables(args, "solve")
    if not xs:
        raise ValueError("solve needs the variable: solve(x)")
    eqs = equations(e)
    if len(xs) == 1 and len(eqs) == 1:
        found = sp.solve(eqs[0], xs[0], dict=False)
        if isinstance(found, dict):
            found = [found[xs[0]]] if xs[0] in found else []
        if not found:
            raise ValueError("no solution found")
        return found[0] if len(found) == 1 else sp.Matrix(found)
    found = sp.solve(eqs, xs, dict=True)
    if not found:
        raise ValueError("no solution found")
    rows = [[s.get(x, x) for x in xs] for s in found]
    return sp.Matrix(rows)


def substitute(e, args):
    pairs = []
    k = 0
    while k < len(args):
        a = args[k]
        if a[0] == "==":
            pairs.append((build(a[1]), build(a[2])))
            k += 1
        elif k + 1 < len(args):
            pairs.append((build(a), build(args[k + 1])))
            k += 2
        else:
            raise ValueError("substitute takes x == value (or x, value)")
    return e.subs(pairs)


def at(args, what):
    """`x` or `x == a`: the variable and the point (0 if not given)."""
    if args and args[0][0] == "==":
        return symbol(args[0][1], what), build(args[0][2])
    if not args:
        raise ValueError("%s needs the variable: %s(x)" % (what, what))
    return symbol(args[0], what), sp.Integer(0)


def whole(args, k, default):
    if len(args) <= k:
        return default
    n = build(args[k])
    if not (n.is_Integer and n > 0):
        raise ValueError("expected a positive whole number")
    return int(n)


def series(e, args):
    x, a = at(args, "series")
    return sp.series(e, x, a, whole(args, 1, 6)).removeO()


def other(args, k, name):
    return build(args[k]) if len(args) > k else sp.Symbol(name)


def laplace(e, args):
    t = symbol(args[0], "laplace") if args else sp.Symbol("t")
    return sp.laplace_transform(e, t, other(args, 1, "s"), noconds=True)


def invlaplace(e, args):
    # Time is positive: sin(t), not sin(t)*Heaviside(t).
    s = symbol(args[0], "invlaplace") if args else sp.Symbol("s")
    t = other(args, 1, "t")
    positive = sp.Symbol(t.name, positive=True)
    return sp.inverse_laplace_transform(e, s, positive)


# Fourier as worksheets traditionally define it: F(ω) = ∫ f(t) e^(-iωt) dt; SymPy's uses e^(-2πikt).
def fourier(e, args):
    t = symbol(args[0], "fourier") if args else sp.Symbol("t")
    w = other(args, 1, "ω")
    k = sp.Dummy("k")
    return sp.fourier_transform(e, t, k).subs(k, w / (2 * sp.pi))


def invfourier(e, args):
    w = symbol(args[0], "invfourier") if args else sp.Symbol("ω")
    t = other(args, 1, "t")
    k = sp.Dummy("k")
    return sp.inverse_fourier_transform(e.subs(w, 2 * sp.pi * k), k, t)


def ztrans(e, args):
    n = symbol(args[0], "ztrans") if args else sp.Symbol("n")
    z = other(args, 1, "z")
    r = sp.summation(e * z ** (-n), (n, 0, sp.oo))
    # Where the sum converges (|z| large enough), as traditionally given: z/(z - 1), without the condition.
    if isinstance(r, sp.Piecewise):
        r = r.args[0][0]
    return sp.simplify(r)


def coeffs(e, args):
    x = symbol(args[0], "coeffs") if args else None
    if x is None:
        raise ValueError("coeffs needs the variable: coeffs(x)")
    c = sp.Poly(e, x).all_coeffs()
    c.reverse()
    return sp.Matrix(c)


ASSUMPTIONS = {"real": "real", "positive": "positive", "negative": "negative", "integer": "integer",
               "nonnegative": "nonnegative", "RealRange": "real"}


def assume(e, args, state):
    """`assume(x > 0)`, `assume(x, real)`: the name becomes a symbol with that property."""
    k = 0
    while k < len(args):
        a = args[k]
        if a[0] in (">", ">=", "<", "<=") and a[1][0] == "v" and build(a[2]) == 0:
            prop = {">": "positive", ">=": "nonnegative", "<": "negative", "<=": "nonpositive"}[a[0]]
            index = a[1][1]
            k += 1
        elif a[0] == "v" and k + 1 < len(args) and args[k + 1][0] == "v":
            index = a[1]
            word = state["words"].get(args[k + 1][1])
            if word not in ASSUMPTIONS:
                raise ValueError("assume: expected real, positive, negative, nonnegative or integer")
            prop = ASSUMPTIONS[word]
            k += 2
        else:
            raise ValueError("assume takes x > 0, x < 0, x >= 0, or a name and a word: assume(x, real)")
        old = var(index)
        new = sp.Symbol(old.name, **{prop: True})
        names[index] = new
        e = e.subs(old, new)
    return e


def apply(e, keyword, state):
    name, args = keyword[0], keyword[1:]
    if name == "simplify":
        return sp.simplify(e)
    if name == "expand":
        return sp.expand(e)
    if name == "factor":
        return sp.factor(e, *variables(args, "factor"))
    if name == "collect":
        return sp.collect(sp.expand(e), variables(args, "collect"))
    if name == "parfrac":
        xs = variables(args, "parfrac")
        return sp.apart(e, xs[0]) if xs else sp.apart(e)
    if name == "float":
        return e.evalf(whole(args, 0, 10))
    if name == "solve":
        return solve(e, args)
    if name == "substitute":
        return substitute(e, args)
    if name == "series":
        return series(e, args)
    if name == "laplace":
        return laplace(e, args)
    if name == "invlaplace":
        return invlaplace(e, args)
    if name == "fourier":
        return fourier(e, args)
    if name == "invfourier":
        return invfourier(e, args)
    if name == "ztrans":
        return ztrans(e, args)
    if name == "coeffs":
        return coeffs(e, args)
    if name == "assume":
        return assume(e, args, state)
    if name == "rewrite":
        raise ValueError("rewrite is not available yet")
    raise ValueError("%s is not a symbolic keyword" % name)


# Back to a tree.

FUNCTION_NAMES = {sp.log: "ln", sp.Abs: "abs", sp.ceiling: "ceil", sp.re: "Re", sp.im: "Im",
                  sp.Heaviside: "Φ", sp.DiracDelta: "δ"}


def tree(e):
    if isinstance(e, (list, tuple)):
        return tree(sp.Matrix(e))
    if isinstance(e, sp.MatrixBase):
        return ["m", e.rows, e.cols] + [tree(a) for a in e]
    if isinstance(e, sp.Piecewise):
        raise ValueError("the result depends on conditions (try assume, such as assume(x > 0))")
    if e is sp.true or e is sp.false:
        raise ValueError("the result is %s" % ("true" if e is sp.true else "false"))
    if e in (sp.zoo, sp.nan):
        raise ValueError("the result is undefined")
    if e is sp.oo:
        return ["oo"]
    if e is sp.S.NegativeInfinity:
        return ["*", ["q", "-1", "1"], ["oo"]]
    if e is sp.pi:
        return ["pi"]
    if e is sp.E:
        return ["e"]
    if e is sp.I:
        return ["I"]
    if isinstance(e, sp.Rational):
        return ["q", str(e.p), str(e.q)]
    if isinstance(e, sp.Float):
        return ["r", str(e)]
    if isinstance(e, sp.Symbol):
        if e.name.startswith("v") and e.name[1:].isdigit():
            return ["v", int(e.name[1:])]
        return ["s", e.name]
    if isinstance(e, sp.Add):
        return ["+"] + [tree(a) for a in e.args]
    if isinstance(e, sp.Mul):
        return ["*"] + [tree(a) for a in e.args]
    if isinstance(e, sp.Pow):
        return ["^", tree(e.base), tree(e.exp)]
    if isinstance(e, sp.exp):
        return ["^", ["e"], tree(e.args[0])]
    if isinstance(e, sp.Rel):
        op = {sp.Eq: "==", sp.Ne: "!=", sp.Lt: "<", sp.Le: "<=", sp.Gt: ">", sp.Ge: ">="}[type(e)]
        return [op, tree(e.lhs), tree(e.rhs)]
    if isinstance(e, sp.Integral):
        f, limits = e.function, e.limits
        if len(limits) != 1:
            raise ValueError("SymPy left a multiple integral")
        lim = limits[0]
        return ["f", "int", tree(f), tree(lim[0])] + [tree(a) for a in lim[1:]]
    if isinstance(e, sp.Derivative):
        x, n = e.variable_count[0]
        return ["f", "diff", tree(e.expr), tree(x)] + ([] if n == 1 else [tree(sp.Integer(n))])
    if isinstance(e, (sp.Sum, sp.Product)):
        i, a, b = e.limits[0]
        return ["f", "sum" if isinstance(e, sp.Sum) else "prod", tree(e.function), tree(i), tree(a), tree(b)]
    if isinstance(e, sp.Limit):
        return ["f", "lim", tree(e.args[0]), tree(e.args[1]), tree(e.args[2])]
    if isinstance(e, sp.Function):
        name = type(e).__name__
        if isinstance(type(e), sp.core.function.UndefinedFunction):
            if name.startswith("F") and name[1:].isdigit():
                return ["u", int(name[1:])] + [tree(a) for a in e.args]
            return ["f", name] + [tree(a) for a in e.args]
        if isinstance(e, sp.Heaviside):
            return ["f", "Φ", tree(e.args[0])]
        return ["f", FUNCTION_NAMES.get(type(e), name)] + [tree(a) for a in e.args]
    raise ValueError("can't show this result yet: " + type(e).__name__)


# --- Python functions in a sheet (docs/symbolic.md#python-functions-in-a-sheet) -------------------

modules = {}


def module(path):
    """The module in the file at path, loaded again when the file changes."""
    import importlib.util
    import os
    stamp = os.path.getmtime(path)
    known = modules.get(path)
    if known and known[0] == stamp:
        return known[1]
    spec = importlib.util.spec_from_file_location("beermat_use_%d" % len(modules), path)
    loaded = importlib.util.module_from_spec(spec)
    # Its own imports may come from its folder, as when it runs as a script.
    folder = os.path.dirname(os.path.abspath(path))
    if folder not in sys.path:
        sys.path.insert(0, folder)
    # A package installed since Python started is found too.
    importlib.invalidate_caches()
    try:
        spec.loader.exec_module(loaded)
    except Exception as x:  # noqa: BLE001
        raise ValueError(failure(x, path)) from None
    modules[path] = (stamp, loaded)
    return loaded


def describe(path):
    """The module's public functions: their parameters, unit annotations, signature and docstring."""
    import inspect
    loaded = module(path)
    out = []
    for name, f in inspect.getmembers(loaded, inspect.isfunction):
        if name.startswith("_") or f.__module__ != loaded.__name__:
            continue
        signature = inspect.signature(f)
        params = []
        for p in signature.parameters.values():
            if p.kind in (p.VAR_POSITIONAL, p.VAR_KEYWORD, p.KEYWORD_ONLY):
                continue
            params.append({"name": p.name, "unit": p.annotation if isinstance(p.annotation, str) else None,
                           "optional": p.default is not p.empty})
        returns = signature.return_annotation
        out.append({"name": name, "params": params, "returns": returns if isinstance(returns, str) else None,
                    "signature": name + str(signature), "doc": inspect.getdoc(f) or ""})
    return out


def value_in(v):
    if isinstance(v, dict) and "c" in v:
        return complex(v["c"][0], v["c"][1])
    if isinstance(v, dict) and "m" in v:
        return [[value_in(x) for x in row] for row in v["m"]]
    return v


def value_out(v):
    if hasattr(v, "tolist"):  # arrays of a package such as NumPy
        v = v.tolist()
    if isinstance(v, bool):
        return 1 if v else 0
    if isinstance(v, (int, float)):
        return float(v)
    if isinstance(v, complex):
        return {"c": [v.real, v.imag]}
    if isinstance(v, str):
        return {"s": v}
    if isinstance(v, (list, tuple)):
        items = list(v)
        if items and all(isinstance(x, (list, tuple)) for x in items):
            return {"m": [[value_out(x) for x in row] for row in items]}
        return {"m": [[value_out(x)] for x in items]}
    if isinstance(v, sp.Basic):
        return value_out(complex(v)) if not v.is_real else float(v)
    raise ValueError("a Python function returned %s, which a sheet can't hold (numbers, lists and strings can)" % type(v).__name__)


def failure(x, path):
    """Python's message, with the line in the user's file where it happened."""
    import os
    import traceback
    frames = [f for f in traceback.extract_tb(x.__traceback__) if os.path.abspath(f.filename) == os.path.abspath(path)]
    where = " (line %d of %s)" % (frames[-1].lineno, os.path.basename(path)) if frames else ""
    return "%s: %s%s" % (type(x).__name__, x, where)


def call(request):
    path = request["path"]
    f = getattr(module(path), request["name"])
    try:
        result = f(*[value_in(a) for a in request["args"]])
    except Exception as x:  # noqa: BLE001
        raise ValueError(failure(x, path)) from None
    return value_out(result)


def answer(request):
    op = request.get("op")
    if op == "describe":
        return describe(request["path"])
    if op == "call":
        return call(request)
    names.clear()
    state = {"words": {int(k): w for k, w in request.get("words", {}).items()}}
    e = build(request["expr"])
    for keyword in request["keywords"]:
        e = apply(e, keyword, state)
    return tree(e)


def main():
    sys.setrecursionlimit(10000)
    out = sys.stdout
    out.write('{"ready": "%s"}\n' % sp.__version__)
    out.flush()
    for line in sys.stdin:
        if not line.strip():
            continue
        try:
            reply = {"ok": answer(json.loads(line))}
        except Exception as x:  # noqa: BLE001 - every failure goes back as a message
            message = str(x) or type(x).__name__
            reply = {"error": message}
        out.write(json.dumps(reply) + "\n")
        out.flush()


main()
