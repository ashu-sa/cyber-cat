"""Isolated deterministic preview; never touches a real timer or history."""
from dataclasses import dataclass
@dataclass
class TreePreviewFrame:
    elapsed:float
    dead:bool
    death:float
    title:str
    @staticmethod
    def at(time):
        t=max(0,min(27,time))
        if t<10:return TreePreviewFrame(t/10*1500,False,0,'01 · A seed becomes a flower')
        if t<12:return TreePreviewFrame(1500,False,0,'02 · 25 minutes complete — flower saved')
        if t<14.4:return TreePreviewFrame(1500+t-12,False,0,'03 · A second tree begins')
        if t<20:return TreePreviewFrame(1502.4+(t-14.4)/5.6*972.6,False,0,'03 · A second tree begins')
        return TreePreviewFrame(2475,True,max(0,min(1,(t-20)/3.4)),'04 · Cancelled — the unfinished tree withers' if t<23.4 else '05 · Completed flower safe · Cancelled tree lost')
