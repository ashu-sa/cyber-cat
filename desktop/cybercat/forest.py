"""Derived rewards: never store counters that can drift from synced history."""
import math
HOUR = 3600
GARDEN = 8 * HOUR

def trees(seconds):
    return int(max(0, seconds) // HOUR) if math.isfinite(seconds) else 0

def tree_text(seconds):
    n = trees(seconds)
    return f'{n} tree' + ('' if n == 1 else 's')

def garden(seconds):
    return math.isfinite(seconds) and seconds >= GARDEN

def cell_text(seconds):
    return 'Garden' if garden(seconds) else tree_text(seconds) if trees(seconds) else 'Growing' if seconds > 0 else '—'

def summary(seconds, daily_totals):
    n = sum(garden(value) for value in daily_totals)
    return tree_text(seconds) + (f' · {n} garden day' + ('' if n == 1 else 's') if n else '')

def growth(seconds):
    seconds = max(0, seconds) if math.isfinite(seconds) else 0
    return min(1, (seconds % HOUR) / HOUR)
