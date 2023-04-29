import numpy as np
import pandas as pd
import matplotlib.pylab as plt
plt.rcParams.update({'font.size': 12})


def simple_plot(data, xlabel, ylabel, ylim, file_name, legend=True):

    plt.plot(np.arange(len(data))*20, data.values)
    plt.xlabel(xlabel)
    plt.xticks()
    plt.yticks(np.arange(0, 19, 2))
    plt.ylabel(ylabel)
    plt.ylim(ylim)
    plt.margins(0)
    plt.subplots_adjust(left=0.15, bottom=0.1, top=0.95, right=0.95)
    # plt.show()
    if legend:
        plt.legend(data.columns.values, loc='lower center', ncol=2)
        #plt.legend(data.columns.values, loc='upper center', ncol=1)
    plt.savefig(file_name)
    plt.clf()

def workload_parallelism(workload, parallelism):
    fig, ax1 = plt.subplots()

    # left
    ax1.set_xlabel('Time (s)')
    ax1.set_ylabel('Workload (tuples / second)')
    ax1.plot(np.arange(len(workload)) * 20, workload, color='black')
    ax1.tick_params(axis='y')
    ax1.set_ylim((0,449999))

    # right
    ax2 = ax1.twinx()  # instantiate a second axes that shares the same x-axis
    ax2.set_ylabel('Parallelism', color='tab:blue')  # we already handled the x-label with ax1
    ax2.plot(np.arange(len(parallelism)) * 20, parallelism, color='tab:blue')
    ax2.set_ylim((-1,13.5))
    ax2.tick_params(axis='y')

    # overprovisioning, underprovisioning
    plt.axhline(y=12, color='r', linestyle='--')
    plt.text(10600, 12.25 , 'over-provisioning', color='r', horizontalalignment='center')

    plt.text(3500, 6.5 , 'autoscaling', color='tab:blue')

    plt.axhline(y=3, color='r', linestyle='--')
    plt.text(10600, 2.25 , 'under-provisioning', color='r', horizontalalignment='center')


    ax1.margins(x=0)
    ax2.margins(x=0)

    fig.tight_layout()  # otherwise the right y-label is slightly clipped

    plt.xticks()
    fig.set_figwidth(10)
    plt.savefig("autoscaling.pdf")
    plt.clf()
    #plt.show()


def plot_ecdf(latency):
    for approach in latency.columns:
        x = np.sort(latency[approach].values)
        y = np.arange(len(x)) / float(len(x))
        plt.plot(x, y)

    plt.xlabel("Latencies (log-scale)")
    plt.xticks()
    plt.xscale("log")
    plt.ylabel("Proportion (%)")
    plt.ylim((0,1))
    plt.legend(latency.columns.values, loc='lower right')
    plt.margins(0)
    plt.subplots_adjust(left=0.15, bottom=0.1, top=0.95, right=0.95)
    plt.savefig("wc_results_latency.pdf")
    plt.clf()

def bar_plot(data):

    # get default colors
    cmap = plt.get_cmap("tab10")
    colors = []
    for i in range(len(data.columns)):
        colors.append(cmap(i))
    plt.bar(data.columns, data.mean().values/12, color=colors)


   # plt.margins(0)
    plt.subplots_adjust(left=0.15, bottom=0.1, top=0.95, right=0.95)
    plt.xlabel('')
    plt.ylabel('Total Resource Usage (Normalized)')
    plt.savefig("wc_results_resource_usage.pdf")
    plt.clf()


workload = pd.read_csv("wc_results_workload.csv", index_col="Time")
latency = pd.read_csv("wc_results_latency.csv", index_col="Time").fillna(value = 0)
parallelism = pd.read_csv("wc_results_parallelism.csv", index_col="Time")

simple_plot(workload, "Time (s)", "Workload (tuples / second)", (0,45000), "wc_results_workload.pdf", legend=False)
simple_plot(parallelism, "Time (s)", "Parallelism", (0,19), "wc_results_parallelism.pdf")
plot_ecdf(latency)
bar_plot(parallelism)


print("parallelism")
for approach in parallelism.columns:
    print(approach, parallelism[approach].values.mean())

print("latency")
for approach in latency.columns:
    print(approach, latency[approach].values.mean())

# autoscaling layered figure
# workload_parallelism(workload, parallelism['Daedalus'])