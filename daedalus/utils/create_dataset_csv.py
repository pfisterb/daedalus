import numpy as np
import matplotlib.pylab as plt

# f=open('sine_100000_max.csv','a')
# f.write('second|adCount\n')

# linear workload
# seconds = 21600
# for i in range(seconds):
#     f.write("{}|{}\n".format(i,i*1000))

# sine wave
cycles = 3 # how many sine cycles
hours = 6
resolution = 60*60*hours # how many datapoints to generate
max = 60000 # highest - 5000

length = np.pi * 2 * cycles
sine_wave = max/2 * np.sin(np.arange(0, length, length / resolution)) + max/2 + 5000

f=open('sine_65000_6h.csv','a')
f.write('second|count\n')
for i, v in enumerate(sine_wave):
    f.write("{}|{}\n".format(i, int(v)))

plt.plot(sine_wave)
plt.show()
