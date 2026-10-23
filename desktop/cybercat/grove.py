"""Deterministic, elapsed-time-driven handoff; no animation state to lose on pause."""
from dataclasses import dataclass

@dataclass(frozen=True)
class GroveTree:
    x: float
    scale: float
    alpha: float

def grove_layout(count, remainder, animated=True, flower=False):
    v=max(0,min(1,remainder/(5.76 if flower else 2.4))) if animated else 1
    ease=v*v*(3-2*v)
    targets=[(45,.86,1),(30,.70,.86),(20,.56,.72),(12,.46,0)]
    result=[]
    for rank in reversed(range(min(count,4))):
        end=targets[rank]
        start=((55 if count==1 else 66),1,1) if rank==0 else targets[rank-1]
        result.append(GroveTree(*(a+(b-a)*ease for a,b in zip(start,end))))
    return result, (66 if count else 55), (ease if count else 1)
