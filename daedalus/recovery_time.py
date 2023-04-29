import time

import numpy as np
import pandas as pd
import datetime as dt
from threading import Thread
from anomaly_detection import AnomalyDetection
from metrics import Metrics
from forecasting import ForecastingModel
import logging
logger = logging.getLogger(__name__)

class MonitorRecoveryTime(Thread):
    def __init__(self, metrics: Metrics, anomaly_detection: AnomalyDetection):
        Thread.__init__(self)

        self.start_ts = int(time.time())
        self.metrics = metrics
        self.anomaly_detection = anomaly_detection

        # accessible after thread is finished
        self.observed_recovery_time = None

    # override the run method, called with Thread.start()
    def run(self):
        # wait a little to catch the anomalies
        time.sleep(30)

        while True:
            workload = self.metrics.get_metric_from_ts(self.metrics.queries['workload'], self.start_ts)
            throughput = self.metrics.get_metric_from_ts(self.metrics.queries['total_throughput'], self.start_ts)

            workload, throughput = workload.align(throughput, join="outer", axis=0, fill_value=0)
            diff = pd.DataFrame(workload.values - throughput.values, index=workload.index)
            anomalies = self.anomaly_detection.detect_anomalies(diff)

            # if the last observation is not an anomaly, we can calculate recovery time
            if not (anomalies['anomalies'].values[-1] > 0):
                self.observed_recovery_time = self.anomaly_detection.get_average_recovery_time(anomalies)
                break

            time.sleep(5)

class PredictRecoveryTime:
    def __init__(self):

        self.checkpoint_interval = 10

        # time that system is down, can be different depending on scaling direction
        self.scale_up_downtime = 30
        self.scale_down_downtime = 15

    def predict_recovery_time(self, capacity_models, metrics, forecasting: ForecastingModel, scaleout):

        current_capacity = capacity_models.get_capacity(capacity_models.current_parallelism)
        new_scaleout_capacity = capacity_models.get_capacity(scaleout)

        if current_capacity == 0 or new_scaleout_capacity == 0:
            print("Current capacity could not be determined when predicting recovery time")
            return

        # needed variables
        checkpoint_interval = 10

        if scaleout < capacity_models.current_parallelism:
            time_to_restart = self.scale_down_downtime
        else:
            time_to_restart = self.scale_up_downtime

        # choose forecast based on last tsf accuarcy
        forecast = forecasting.get_forecast()

        # calculate backlog
        messages_since_checkpoint = metrics.latest['workload'][-checkpoint_interval:].values.sum()  # worst case
        messages_during_rescale_restart = forecast[:time_to_restart].values.sum()
        consumer_lag = metrics.latest['consumer_lag'].fillna(0).values[-1]
        accumulated_messages = int(messages_since_checkpoint + messages_during_rescale_restart + consumer_lag)

        extra_capacity = pd.DataFrame(new_scaleout_capacity - forecast[time_to_restart:],
                                      index=forecast[time_to_restart:].index)
        extra_capacity[extra_capacity < 0] = 0

        # TODO: fix time (daylight savings)
        start_ts = dt.datetime.now()-dt.timedelta(hours=2)  # to match server ts

        # handle cases where not enough processing capacity, idxmax will return first maximum value
        cumulative_extra_capacity = extra_capacity.cumsum().gt(accumulated_messages)
        if cumulative_extra_capacity.values.sum() > 0:
            recovered_ts = cumulative_extra_capacity.idxmax()[0]
        else:
            # placeholder value to signify that recovery time is longer than forecast
            recovered_ts = dt.datetime.now()+dt.timedelta(hours=1)

        logger.info(f"Predicting recovery time for {capacity_models.current_parallelism} -> {scaleout}")
        logger.info(f"Predicted recovery time: {pd.Timedelta(recovered_ts-start_ts).seconds + time_to_restart}")
        logger.debug(f"New scaleout capacity {new_scaleout_capacity}")
        logger.debug(f"start ts {start_ts}")
        logger.debug(f"recovered ts {recovered_ts}")
        logger.debug(f"recovery time in seconds {pd.Timedelta(recovered_ts-start_ts).seconds}")
        logger.debug(f"average extra capacity: {extra_capacity.mean().values}")
        logger.debug(f"messages_since_checkpoint: {messages_since_checkpoint}")
        logger.debug(f"consumer lag: {consumer_lag}")
        logger.debug(f"messages_during_rescale_restart: {messages_during_rescale_restart}")
        logger.debug(f"accumulated_messages: {accumulated_messages}")

        return pd.Timedelta(recovered_ts-start_ts).seconds + time_to_restart
