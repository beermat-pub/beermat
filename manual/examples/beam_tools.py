def moment(q: "kN/m", L: "m") -> "kN*m":
    """The largest bending moment of a simply supported beam."""
    return q * L**2 / 8

def add(a, b):
    return a + b
