import pandas as pd
import matplotlib.pyplot as plt
import time
from sklearn.preprocessing import PolynomialFeatures
from sklearn.metrics import r2_score
from sklearn.linear_model import HuberRegressor, LinearRegression, SGDRegressor
import numpy as np

class RegressionModel:
    def __init__(self):
        self.count = 0
        self.meanX = 0
        self.meanY = 0
        self.covXY = 0
        self.varX = 0

    def regression_test(self):
        cpu = pd.read_csv("data/testing/sine_cpu_p2.csv", index_col="Time")
        throughput = pd.read_csv("data/testing/sine_throughput_p2.csv", index_col="Time")

        x_all = pd.DataFrame()
        y_all = pd.DataFrame()
        capacity_all = pd.DataFrame()

        for taskmanager in cpu.columns:
            x = cpu[taskmanager].values
            y = throughput[taskmanager].values

            capacity = throughput[taskmanager]/cpu[taskmanager]

            self.update(x, y)

            x_all = pd.concat([x_all, cpu[taskmanager]])
            y_all = pd.concat([y_all, throughput[taskmanager]])
            capacity_all = pd.concat([capacity_all, capacity])

            print("score",  self.score(x, y))
            print("predict", self.predict(1))
            break

        self.plot_regression(x_all, capacity_all)
        # self.plot_regression(x_all.values, y_all.values)

    def update(self, x, y):
        self.count += len(x)
        dx = x - [self.meanX]
        dy = y - [self.meanY]
        self.varX += np.sum(((self.count-1)/self.count)*dx*dx - self.varX)/self.count
        self.covXY += np.sum(((self.count-1)/self.count)*dx*dy - self.covXY)/self.count
        self.meanX += np.sum(dx)/self.count
        self.meanY += np.sum(dy)/self.count

    def predict(self, x):
        # y = m*x + b
        m = self.get_slope()
        b = self.get_intercept()
        return m*x + b

    def score(self, x, y):
        return r2_score(y, self.predict(x))

    def get_slope(self):
        return self.covXY/self.varX

    def get_intercept(self):
        return self.meanY - self.get_slope()*self.meanX

    def plot_regression(self, x, y):
        plt.scatter(x, y, s=2)
        plt.plot(x, self.predict(x), color='black', linewidth=1)
        # plt.title(taskmanager)
        plt.ylabel("Estimated Capacity")
        plt.xlabel("CPU")
        plt.margins(0)
        plt.subplots_adjust(left=0.16, bottom=0.1, top=0.95, right=0.95)
        plt.savefig("simple_capacity.pdf")
        plt.show()