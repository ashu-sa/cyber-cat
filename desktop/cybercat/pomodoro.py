"""Manual-next-round Pomodoro; focus is capped and breaks never count as work."""
FOCUS = 25 * 60

class Pomodoro:
    def __init__(self, data=None, clock=None, wall=None):
        import time
        self.clock, self.wall = clock or time.monotonic, wall or time.time
        d=data or {}
        self.stage=d.get('stage','focus')
        self.target=float(d.get('target',FOCUS))
        self.break_minutes=10 if d.get('break_minutes')==10 else 5
        self.deadline=d.get('deadline')
        self.end=self.clock()+max(0,self.deadline-self.wall()) if self.deadline else None
    def dump(self):
        return dict(stage=self.stage,target=self.target,break_minutes=self.break_minutes,deadline=self.deadline)
    def start(self,elapsed):
        if self.stage in ('break','ready'):
            self.stage='focus'; self.target=elapsed+FOCUS; self.deadline=self.end=None
    def remaining(self,elapsed):
        if self.stage=='break': return max(0,(self.end or self.clock())-self.clock())
        if self.stage=='ready': return 0
        return max(0,self.target-elapsed)
    def advance(self,elapsed,running):
        cap=None; notice=None
        if self.stage=='focus' and running and elapsed>=self.target:
            cap=self.target
            remaining=self.break_minutes*60-(elapsed-self.target)
            self.deadline=self.wall()+remaining; self.end=self.clock()+remaining
            self.stage='break'; notice='Your flower bloomed! Stop watering, stretch and move a little.'
        if self.stage=='break' and self.remaining(elapsed)<=0:
            self.stage='ready'; notice='Come back when you’re ready — your next flower needs water.'
        return cap,notice
