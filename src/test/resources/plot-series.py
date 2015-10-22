#!/usr/bin/env python
import fileinput
import re
import sys
import json
import matplotlib.pyplot as plt
from matplotlib.dates import YearLocator, MonthLocator, DateFormatter
from datetime import datetime

with open(sys.argv[1]) as f:
    data = json.loads(f.read())

ys = (data['series'], data['trend'], data['seasonal'], data['remainder'])
ts = map(lambda x: datetime.fromtimestamp(x), data['times'])
labels = ['series', 'trend', 'seasonal', 'remainder', 'series and trend']

years = YearLocator()   # every year
months = MonthLocator()  # every month
years_formatter = DateFormatter('%Y')

fig, axes = plt.subplots(nrows=5)

for i in xrange(4):
    ax = axes[i]
    ax.plot_date(ts, ys[i], '-')

    # format the ticks
    ax.xaxis.set_major_locator(years)
    ax.xaxis.set_major_formatter(years_formatter)
    ax.xaxis.set_minor_locator(months)
    ax.autoscale_view()
    ax.set_ylabel(labels[i])

# Plot both series and trend on last axis
axes[4].plot_date(ts, ys[0], '-')
axes[4].plot_date(ts, ys[1], '-')
axes[4].set_ylabel(labels[4])

fig.autofmt_xdate()
plt.show()
