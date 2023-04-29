import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
plt.rcParams.update({'font.size': 12})

def plot_data_skew():
    cpu = pd.read_csv("data/figures/data_skew_cpu_12.csv", index_col="Time")
    throughput = pd.read_csv("data/figures/data_skew_throughput_12.csv", index_col="Time")
    avg_cpu = pd.read_csv("data/figures/data_skew_total_cpu.csv", index_col="Time")
    total_throughput = pd.read_csv("data/figures/data_skew_total_throughput.csv", index_col="Time")

    simple_plot(cpu, 15, "Time (s)", "CPU", (0, 1.1), "data/figures/data_skew_cpu_12.pdf")
    simple_plot(throughput, 15, "Time (s)", "Throughput (tuples / s)", (0, 65000), "data/figures/data_skew_throughput_12.pdf")
    simple_plot(total_throughput, 15, "Time (s)", "Throughput (tuples / s)", (0, 650000), "data/figures/data_skew_total_throughput_12.pdf")
    simple_plot(avg_cpu, 15, "Time (s)", "Utilization (%)", (0, 1.1), "data/figures/data_skew_avg_cpu_12.pdf")

def plot_assumptions():
    cpu = pd.read_csv("data/figures/cpu_1.csv", index_col="Time")
    throughput = pd.read_csv("data/figures/throughput_1.csv", index_col="Time")
    latency = pd.read_csv("data/figures/latency_1.csv", index_col="Time")
    workload = pd.read_csv("data/figures/workload_1.csv", index_col="Time")

    simple_plot(cpu, 15, "Time (s)", "Utilization (%)", (0, 1.1), "data/figures/assumptions_cpu_1.pdf")
    simple_plot(throughput, 15, "Time (s)", "Throughput (tuples / s)", (0, 80000), "data/figures/assumptions_throughput_1.pdf")
    simple_plot(workload, 15, "Time (s)", "Workload (tuples / s)", (0, 80000), "data/figures/assumptions_workload_1.pdf")
    simple_plot(latency, 15, "Time (s)", "End-to-end Latency (ms)", (0, 60000), "data/figures/assumptions_latency_1.pdf")

def plot_workloads():
    ysb = pd.read_csv("data/figures/advertising_6h_550K_max.csv", sep="|", index_col="second")
    tm = pd.read_csv("data/figures/tm_6h_100K_max.csv", sep="|", index_col="second")
    wc = pd.read_csv("data/figures/sine_60000_6h.csv", sep="|", index_col="second")

    simple_plot(ysb, 1, "Time (s)", "Workload (tuples / second)", (0,590000), "data/figures/ysb_workload.pdf")
    simple_plot(tm, 1, "Time (s)", "Workload (tuples / second)", (0,110000), "data/figures/tm_workload.pdf")
    simple_plot(wc, 1, "Time (s)", "Workload (tuples / second)", (0,65000), "data/figures/wc_workload.pdf")

def simple_plot(data, step, xlabel, ylabel, ylim, file_name):
    plt.plot(np.arange(len(data)) * step, data.values)
    plt.xlabel(xlabel)
    plt.xticks()
    plt.ylabel(ylabel)
    plt.ylim(ylim)
    plt.margins(0)
    plt.subplots_adjust(left=0.15, bottom=0.1, top=0.95, right=0.95)
    plt.savefig(file_name)
    plt.clf()