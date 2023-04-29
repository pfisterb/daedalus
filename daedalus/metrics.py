import time
from clients import PrometheusClient
import pandas as pd
import matplotlib.pyplot as plt


class Metrics:
    def __init__(self, config):

        self.prometheus = PrometheusClient(config.prometheus_url)

        self.latest = {
            'cpu': pd.DataFrame(),
            'throughput': pd.DataFrame(),
            'total_throughput': pd.DataFrame(),
            'latency': pd.DataFrame(),
            'total_latency': pd.DataFrame(),
            'workload': pd.DataFrame(),
            'parallelism': pd.DataFrame(),
            'uptime': pd.DataFrame(),
            'consumer_lag': pd.DataFrame(),
        }
        self.queries = config.daedalus_queries

    def collect_metrics(self, loop_time):

        start = int(time.time()) - loop_time - 1
        stop = int(time.time())
        step = 1

        for key, query in self.queries.items():
            self.latest[key] = self.prometheus.query_range(query, start, stop, step)

    def get_metric_from_ts(self, query, start_ts):
        return self.prometheus.query_range(query, start_ts, int(time.time()), 1)

    def metrics_to_file(self):
        for key in self.entire.keys():
            self.entire[key].to_csv('data/{}_{}.csv'.format(key, int(time.time())))
