#!/usr/bin/env python
import fileinput
import re
import json
import matplotlib.pyplot as plt
from matplotlib.dates import YearLocator, MonthLocator, DateFormatter
from datetime import datetime

ts = []
ys = []

for line in fileinput.input():
    tokens = re.split('\s+', line.rstrip())
    year = int(tokens[0])
    for i in xrange(1, len(tokens)):
        month = '{0}-{1:02d}'.format(year, i)
        value = float(tokens[i])
        time = datetime.strptime(month, '%Y-%m')
        ts.append(time)
        ys.append(value)

output = {
    'ts': map(lambda x: int(x.strftime("%s")), ts),
    'ys': ys
}

print json.dumps(output)

# years = YearLocator()   # every year
# months = MonthLocator()  # every month
# years_formatter = DateFormatter('%Y')
#
# fig, ax = plt.subplots()
# ax.plot_date(ts, ys, '-')
#
# # format the ticks
# ax.xaxis.set_major_locator(years)
# ax.xaxis.set_major_formatter(years_formatter)
# ax.xaxis.set_minor_locator(months)
# ax.autoscale_view()
#
# fig.autofmt_xdate()
# plt.show()
