import pandas as pd
import matplotlib.pyplot as plt
from sklearn.preprocessing import MinMaxScaler

df = pd.read_csv("advertising_1D_increased_load.csv", sep="|", index_col="second")
# df = pd.read_csv("IoT_7D_1S_25K_max.csv", sep="|", index_col="seconds")
# df = df[0:100000]
# print(len(df))
# rescale to max capacity
y_min = 0
y_max = 150000
scaler = MinMaxScaler(feature_range=(y_min, y_max))
df = scaler.fit_transform(df)


# rescale to 6 hour time 
time = 60 * 60 * 6
sample = len(df) // time
df = pd.DataFrame(df[::sample])
df = df.astype(int)

df.to_csv("ysb_6h_150K_max.csv", sep="|", header=True)

plt.plot(df)
plt.show()


