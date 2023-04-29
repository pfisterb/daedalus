from metrics import Metrics
import pmdarima as pm
import pandas as pd
import matplotlib.pyplot as plt
import pickle
from threading import Thread
import numpy as np
import time
from scipy.stats import linregress

import logging
logger = logging.getLogger(__name__)

class ForecastingModel:
    def __init__(self):
        self.arima_model = None

        # prediction using arima
        self.accuracy = np.inf  # the lower the better
        self.tsf = pd.DataFrame()

        # prediction using workload linear regression
        self.fallback = pd.DataFrame()
        self.workload_slope = None

        # count of consecutive bad predictions
        self.bad_predictions = 0

    def analyze(self, m: Metrics):

        if not self.arima_model:
            self.train_arima(m.latest['workload'])
        else:
            self.update(m.latest['workload'])

        # evaluate previous forecast
        # check accuracy of last forecast (before next forecast)
        self.accuracy = self.wape(m.latest['workload'], self.tsf)
        logger.info(f"Forecasting WAPE accuracy {self.accuracy}")

        # predict 15 minutes into the future
        self.predict(steps_to_predict=900)
        #self.plot(m.latest['workload'], self.tsf)

        # calculate fallback prediction using workload regression
        self.workload_regression(m.latest['workload'])

        #self.plot(m.latest['workload'], self.fallback)

    def train_arima(self, workload: pd.DataFrame, persist=False):

        # find differencing term
        kpss_diffs = pm.arima.ndiffs(workload, alpha=0.05, test='kpss', max_d=6)
        adf_diffs = pm.arima.ndiffs(workload, alpha=0.05, test='adf', max_d=6)
        n_diffs = max(adf_diffs, kpss_diffs)

        self.arima_model = pm.auto_arima(workload, start_p=0, d=n_diffs, start_q=0, max_p=5, max_q=5,
                                 start_P=0, D=0, start_Q=0, max_P=0, max_D=0, max_Q=0,
                                 seasonal=False, error_action='warn', trace=False, supress_warnings=True, stepwise=True,
                                 random_state=20, n_fits=50)

        if persist:
            self.save_model('arima.pkl')

    def predict(self, steps_to_predict=900):
        prediction = self.arima_model.predict(n_periods=steps_to_predict)

        # create tsf forecast and add timestamp as index
        self.tsf = pd.DataFrame(prediction)
        self.tsf.index = pd.date_range(time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime()), periods=len(prediction), freq='S')

    def update(self, data, persist=False):
        self.arima_model.update(data)

        if persist:
            self.save_model('arima.pkl')

    # weighted absolute percentage error (wape)
    def wape(self, workload, forecast):
        # align workload and forecast
        workload, forecast = workload.align(forecast, join="inner", axis=0)

        # check if dfs could be aligned
        if workload.empty or forecast.empty:
            logger.warning("Workload and forecast could not be aligned")
            return np.inf

        error = np.sum(np.abs(workload.values - forecast.values))/np.sum(workload.values)
        if error < 0.10:
            self.bad_predictions = 0
        else:
            self.bad_predictions += 1
            logger.warning(f"Number of consecutive bad predictions: {self.bad_predictions}")

        return error

    def get_forecast(self):
        # choose forecast based on last tsf accuracy
        if self.accuracy < 0.25:
            forecast = self.tsf
        else:
            forecast = self.fallback


        return forecast



    def load_model(self, path):
        with open('arima.pkl', 'rb') as pkl:
            self.arima_model = pickle.load(pkl)

    def save_model(self, path):
        with open(path, 'wb') as pkl:
            pickle.dump(self.arima_model, pkl)


    def workload_regression(self, workload):
        # create a simple regression model from latest workload
        x = np.arange(0, len(workload))
        y = workload.to_numpy().flatten()
        wr = linregress(x, y)

        # predict 600 seconds into the future
        predictions = []
        for x in range(len(workload), len(workload)+600):
            # predict with: y = mx + b
            predictions.append(wr.slope * x + wr.intercept)

        # create fallback forecast and add timestamp as index
        self.fallback = pd.DataFrame(predictions)
        self.fallback.index = pd.date_range(time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime()), periods=len(predictions), freq='S')
        self.workload_slope = wr.slope

    def plot(self, train, prediction):
        plt.figure(figsize=(8, 5))
        plt.plot(train, label="Workload")
        plt.plot(prediction, label="Prediction")
        plt.legend(loc='best')
        plt.show()

class RetrainModel(Thread):
    def __init__(self, training_workload):
        Thread.__init__(self)

        self.training_workload = training_workload
        self.forecasting_model = ForecastingModel()

    # override the run method
    def run(self):
        logger.info("Retraining ARIMA in a separate thread")
        self.forecasting_model.train_arima(self.training_workload)