import time
from config import Config
from experiment_manager import ExperimentManager
from metrics import Metrics
from capacity import CapacityModels
from clients import PrometheusClient, KubernetesClient
from forecasting import ForecastingModel, RetrainModel
from anomaly_detection import AnomalyDetection
from recovery_time import MonitorRecoveryTime, PredictRecoveryTime

import logging
logger = logging.getLogger(__name__)

class ExecutionManager:
    def __init__(self):
        self.config = Config()

        # classes
        self.experiment_manager = ExperimentManager(self.config)
        self.metrics = Metrics(self.config)
        self.capacity_models = CapacityModels(self.config.max_scaleout)
        self.forecasting = ForecastingModel()
        self.anomaly_detection = AnomalyDetection()
        self.recovery_time = PredictRecoveryTime()

        # clients
        self.prometheus = PrometheusClient(self.config.prometheus_url)
        self.kubernetes = KubernetesClient()

        # scaling
        self.scaling_action = []
        self.last_rescale = time.time()-300  # Don't rescale in the first 5 minutes

        # predicting recovery time
        self.checkpoint_interval = 10

        # for observing recovery time
        self.scale_direction = None
        self.predicted_recovery_time = 0
        self.observed_recovery_time = 0
        self.monitoring_recovery_time = False
        self.recovery_time_thread = None

        # for retraining forecasting model
        self.retraining_model = False
        self.retraining_model_thread = None

    def mape(self):
        while True:
            self.monitor()
            self.analyze()
            self.plan()
            self.execute()
            time.sleep(self.config.loop_time)

    def monitor(self):
        self.metrics.collect_metrics(self.config.loop_time)

        # retrain ARIMA thread finished running
        if self.retraining_model and not(self.retraining_model_thread.is_alive()):
            print("Updating arima model")
            self.forecasting.arima_model = self.retraining_model_thread.forecasting_model.arima_model
            self.retraining_model = False

        # monitoring recovery time thread finished running
        if self.monitoring_recovery_time and not(self.recovery_time_thread.is_alive()):
            self.observed_recovery_time = self.recovery_time_thread.observed_recovery_time
            logger.info(f"Actual recovery time: {self.observed_recovery_time}")

            # adjust downtime to better predict recovery time
            self.adjust_downtime()

            self.monitoring_recovery_time = False

    def analyze(self):
        # capacity model
        self.capacity_models.analyze(self.metrics)

        # anomaly detection
        self.anomaly_detection.anomaly_detection(self.metrics)

        # time series forecasting
        self.forecasting.analyze(self.metrics)

        # retrain ARIMA if predictions are consistently poor
        if self.forecasting.bad_predictions >= 15:
            total_workload = self.metrics.get_metric_from_ts(self.prometheus, self.metrics.queries['workload'], self.experiment_manager.experiment_start)
            self.retraining_model = True
            self.retraining_model_thread = RetrainModel(total_workload)
            self.retraining_model_thread.start()

    def plan(self):
        # wait at least 3 mins to stabilize last rescale to prevent flapping
        if time.time() - self.last_rescale < 180:
            logger.info(f"Last rescale: {time.time() - self.last_rescale}")
            return

        # don't scale if capacity info not available
        if self.capacity_models.get_capacity(self.capacity_models.current_parallelism) == 0:
            logger.warning("Current capacity could not be determined")
            return

        # determine scaleout and rescale if necessary
        scaleout = self.determine_scaleout()
        if scaleout == self.capacity_models.current_parallelism:
            logger.info("Already at optimal scaleout")
        else:
            self.scaling_action.append(scaleout)
            logger.info(f"Scaling to {scaleout}")
            # for adjusting recovery time
            if scaleout < self.capacity_models.current_parallelism:
                self.scale_direction = "down"
            else:
                self.scale_direction = "up"
            return

    def execute(self):
        if len(self.scaling_action) > 0:

            scaleout = self.scaling_action.pop()
            self.last_rescale = time.time()

            self.kubernetes.rescale(self.config.rescale_name, self.config.namespace, scaleout)

            self.recovery_time_thread = MonitorRecoveryTime(self.metrics, self.anomaly_detection)
            self.recovery_time_thread.start()
            self.monitoring_recovery_time = True

            # wait 200 seconds to stabilize kafka before monitoring again
            if self.config.dsp_framework == "kafka":
                time.sleep(200)

    def determine_scaleout(self):

        current_workload = self.capacity_models.avg_current_workload
        time_since_rescale = int(time.time() - self.last_rescale)
        current_parallelism = self.capacity_models.current_parallelism
        current_capacity = self.capacity_models.get_capacity(current_parallelism)
        forecast = self.forecasting.get_forecast()
        logger.info(f"Max forecast {forecast.values.max()}")

        # try to minimize scaling decisions before 10 minutes
        if time_since_rescale < 600:
            # check if rescaling is absolutely necessary
            if current_capacity > current_workload and current_capacity > forecast[:self.config.loop_time].values.max():
                logger.debug(f"Minimizing rescaling under 10 mins")
                return current_parallelism

        # iterate over all scale-outs
        for i in range(self.config.max_scaleout+1):

            # get capacity for scaleout i
            capacity = self.capacity_models.get_capacity(i)

            # find the first scaleout that can handle the current workload
            if capacity > current_workload:

                logger.info(f"Examining: {current_parallelism} -> {i}")

                # continue to next scaleout if above recovery time target
                recovery_time = self.recovery_time.predict_recovery_time(self.capacity_models, self.metrics, self.forecasting, i)
                self.predicted_recovery_time = recovery_time
                if recovery_time > self.config.target_recovery_time:
                    logger.info(f"Examined scaleout recovery time too high: "
                                   f"{recovery_time} > {self.config.target_recovery_time}")
                    continue

                if capacity < forecast[:recovery_time].values.max():
                    logger.info(f"Capacity is less than recovery forecast {capacity} < {forecast[:recovery_time].values.max()}")
                    continue

                # i is an acceptable scale-out
                if i == current_parallelism:
                    return i

                # don't scale down if lag is greater than capacity
                if i < current_parallelism:
                    if self.capacity_models.get_capacity(current_parallelism) < self.metrics.latest['consumer_lag'].fillna(0).values[-1]:
                        logger.info(f"Trying to scale down with large backlog")
                        continue

                # if rescaling is necessary, use maximum TSF for a long-lived scaling decision
                if capacity > forecast.values.max():
                    return i
                logger.info(f"Examined scaleout below forecast. "
                               f"{capacity} < {forecast.values.max()} with recovery time {recovery_time}")


        # otherwise max scaleout is the best
        logger.warning("Attempting to scale past max scaleout")
        return self.config.max_scaleout

    def adjust_downtime(self):
        if self.observed_recovery_time > self.predicted_recovery_time:
            factor = 1
        else:
            factor = -1

        if self.scale_direction == "up":
            self.recovery_time.scale_up_downtime += factor
        else:
            self.recovery_time.scale_down_downtime += factor
