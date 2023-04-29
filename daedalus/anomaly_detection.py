import pandas as pd
import matplotlib.pyplot as plt
from metrics import Metrics
import numpy as np
import logging
logger = logging.getLogger(__name__)

class AnomalyDetection:
    def __init__(self):
        self.count = 0
        self.mean = 0
        self.m2 = 0

        self.gap_to_ignore = 30

    # for testing
    def anomaly_detection(self, m: Metrics):

        #workload = m.latest['workload']
        #throughput = m.latest['total_throughput']
        workload = pd.read_csv("data/figures/anomaly_detection_workload.csv", index_col="Time")
        throughput = pd.read_csv("data/figures/anomaly_detect_throughput.csv", index_col="Time")

        # make sure that timeseries have same timestamps
        workload, throughput = workload.align(throughput, join="outer", axis=0, fill_value=0)
        diff = pd.DataFrame(workload.values - throughput.values, index=workload.index)

        # update the model with new values
        self.update(diff.values)

    def detect_anomalies(self, df):
        anomalies = pd.DataFrame()
        anomalies['anomalies'] = abs(df - self.mean) > self.get_std()

        # number of steps to tolerate between groups of anomalies, will add to end
        anomalies['anomalies'] = anomalies['anomalies'].rolling(window=(1+self.gap_to_ignore), min_periods=1).max()

        return anomalies

    def get_average_recovery_time(self, anomalies):

        # group anomalies and non-anomalies, incrementally assigning group
        anomalies['grouped'] = (anomalies['anomalies'].shift() != anomalies['anomalies']).cumsum()

        # sum of each consecutive group
        group_sum = anomalies.groupby('grouped').sum() - self.gap_to_ignore

        # return average sum of grouped anomalies
        # check if there were any anomalies
        if len(group_sum[group_sum['anomalies'] > 0].values) > 0:
            return group_sum[group_sum['anomalies'] > 0].values.mean()
        logger.info("No anomalies detected")
        return 0

    def update(self, values):
        self.count += len(values)
        # new values - old mean
        delta = values - [self.mean]
        self.mean += np.sum(delta) / self.count
        # new values - new mean
        delta2 = values - self.mean
        self.m2 += np.sum(delta * delta2)

    def get_std(self):
        return np.sqrt(self.m2/self.count)

    def plot(self, workload, throughput, diff, anomalies):
        # plot workload vs. throughput
        #plt.figure(figsize=(8, 5))
        plt.plot(throughput, label="Throughput")
        plt.plot(workload, label="Workload")
        plt.scatter(throughput[anomalies['anomalies'] == True].index,
                    throughput[anomalies['anomalies'] == True].values, c='red', label='Outlier')
        plt.xticks()
        plt.ylabel("Workload (tuples / second)")
        plt.xlabel("Time (s)")
        plt.legend(loc='best')
        plt.margins(0)
        plt.subplots_adjust(left=0.15, bottom=0.1, top=0.95, right=0.95)
        plt.savefig("AD-anomalies.pdf")
        plt.show()

        #plt.figure(figsize=(8, 5))
        plt.plot(diff, label="Difference")
        plt.scatter(diff[anomalies['anomalies'] == True].index,
                    diff[anomalies['anomalies'] == True].values, c='red', label='Outlier')
        plt.fill_between(throughput.index, diff.mean() - diff.std(), diff.mean() + diff.std(), color='orange',
                         alpha=0.3)
        plt.xticks()
        plt.ylabel("Difference (Workload - Throughput)")
        plt.xlabel("Time (s)")
        plt.ylim((-20000, 40000))
        plt.legend(loc='best')
        plt.margins(0)
        plt.subplots_adjust(left=0.20, bottom=0.1, top=0.95, right=0.95)
        plt.savefig("AD-difference.pdf")
        plt.show()