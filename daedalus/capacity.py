import sys
import time
import numpy as np
from scipy.stats import linregress
import matplotlib.pyplot as plt
from metrics import Metrics
from regression import RegressionModel
import logging
logger = logging.getLogger(__name__)

class CapacityModels:
    def __init__(self, max_scaleout):

        # maximum scaleout
        self.max_scaleout = max_scaleout

        # taskmanager-level dictionaries of form taskmanager_name: value
        self.simple_capacity = {}
        self.regression_capacity = {}
        self.regression_models = {}

        # max capacity per parallelism, using max_scaleout 12
        self.estimated_capacity = dict.fromkeys(range(self.max_scaleout+1), 0)
        self.observed_capacity = dict.fromkeys(range(self.max_scaleout+1), 0)

        # useful things
        self.max_current_workload = None
        self.avg_current_workload = None
        self.min_current_workload = None
        self.workload_regression = None

        self.current_parallelism = None
        self.current_capacity = None

    def analyze(self, m: Metrics):
        self.set_current_parallelism(m)
        self.analyze_current_workload(m)
        self.analyze_capacity(m)

    def set_current_parallelism(self, m: Metrics):
        parallelism = m.latest['parallelism']
        self.current_parallelism = int(parallelism.values[-1])

    def analyze_current_workload(self, m: Metrics):
        self.max_current_workload = int(m.latest['workload'].values.max())
        self.avg_current_workload = int(m.latest['workload'].values.mean())
        self.min_current_workload = int(m.latest['workload'].values.min())

    def update_capacity_models(self):
        # return if capacity hasn't been calculated yet
        if not self.regression_capacity:
            return

        # update observed capacity with decaying average
        if self.observed_capacity[self.current_parallelism] == 0:
            self.observed_capacity[self.current_parallelism] = self.current_capacity
        else:
            self.observed_capacity[self.current_parallelism] = int((self.observed_capacity[self.current_parallelism] + self.current_capacity)/2)

        # estimate capacity for all scale-outs based on current average capacity
        for parallelism in range(1, self.max_scaleout+1):
            self.estimated_capacity[parallelism] = int(parallelism * (self.current_capacity / self.current_parallelism))

    def analyze_capacity(self, m: Metrics):
        cpu = m.latest['cpu']
        throughput = m.latest['throughput']

        # ensure timeseries are aligned
        cpu, throughput = cpu.align(throughput, join="inner")  # or outer
        cpu, throughput = self.clean(m, cpu, throughput)
        self.clean_models(cpu.columns)

        for taskmanager in cpu.columns:

            # check for empty / nan
            nan_indices = throughput.index[throughput[taskmanager].isna()].union(cpu.index[cpu[taskmanager].isna()])
            cleaned_throughput = throughput[taskmanager].drop(nan_indices)
            cleaned_cpu = cpu[taskmanager].drop(nan_indices)

            # check if values still exist after cleaning
            if cleaned_throughput.empty or cleaned_cpu.empty:
                logger.warning("cleaned cpu or throughput empty")
                return

            ### Regression
            # update taskmanager regression model
            if taskmanager not in self.regression_models:
                self.regression_models[taskmanager] = RegressionModel()
            self.regression_models[taskmanager].update(cleaned_cpu.values, cleaned_throughput.values)

            # predict max capacity, taking data skew into account
            skew_factor = 1 - (cpu.mean().max() - cleaned_cpu.values.mean())
            #logger.info(f"worker: {taskmanager}, skew_factor: {skew_factor}, max_cpu: {cpu.mean().max()}")
            self.regression_capacity[taskmanager] = int(self.regression_models[taskmanager].predict(skew_factor))

            ### Simple capacity, capacity = (throughput/cpu)
            ## place higher weight on observations with a higher CPU, as they are more accurate
            # capacity = cleaned_throughput / cleaned_cpu
            # weighted_capacity = int(sum(capacity * cleaned_cpu) / sum(cleaned_cpu))

            ## use a decaying average, more weight for more recent observations
            # decaying_average = (weighted_capacity + self.simple_capacity.get(taskmanager, weighted_capacity)) / 2
            # self.simple_capacity[taskmanager] = decaying_average

        # calculate current capacity and update models
        self.current_capacity = int(sum(self.regression_capacity.values()))
        self.update_capacity_models()
        self.print_capacity_model()

    def get_capacity(self, parallelism):
        # return observed capacity if possible
        if self.observed_capacity[parallelism] != 0:
            return self.observed_capacity[parallelism]
        else:
            return self.estimated_capacity[parallelism]

    def clean(self, m, cpu, throughput):
        # remove warmup metrics
        uptime = m.latest['uptime']
        warm_up_indices = uptime[uptime.values < 120000].index

        pre_len = len(cpu)

        cpu = cpu.drop(warm_up_indices, errors="ignore")
        throughput = throughput.drop(warm_up_indices, errors="ignore")

        logger.debug(f"dropped indices: {pre_len - len(cpu)}")

        return cpu, throughput

    def clean_models(self, active_taskmanagers):

        for taskmanager in list(self.simple_capacity.keys()):
            if taskmanager not in active_taskmanagers:
                del self.simple_capacity[taskmanager]

        for taskmanager in list(self.regression_capacity.keys()):
            if taskmanager not in active_taskmanagers:
                del self.regression_capacity[taskmanager]

        for taskmanager in list(self.regression_models.keys()):
            if taskmanager not in active_taskmanagers:
                del self.regression_models[taskmanager]

    def print_capacity_model(self):
        logger.info("--------------------------------------------")
        logger.info(f"estimated capacity: {self.estimated_capacity}")
        logger.info(f"observed capacity: {self.observed_capacity}")
        logger.debug(f"worker capacity: {self.regression_capacity}")
        logger.info(f"current_parallelism: {self.current_parallelism}")
        logger.info(f"current capacity: {self.current_capacity}")
        logger.info(f"avg_current_workload: {self.avg_current_workload}")
