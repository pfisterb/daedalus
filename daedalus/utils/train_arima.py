import pmdarima as pm
import pandas as pd
import matplotlib.pyplot as plt
import pickle

workload = pd.read_csv("yahoo_streaming_benchmark_experiment/producer/src/main/resources/sine_100000_max_3h.csv", delimiter="|", index_col="second")

# find differencing term
kpss_diffs = pm.arima.ndiffs(workload, alpha=0.05, test='kpss', max_d=6)
adf_diffs = pm.arima.ndiffs(workload, alpha=0.05, test='adf', max_d=6)
n_diffs = max(adf_diffs, kpss_diffs)

arima_model = pm.auto_arima(workload, start_p=0, d=n_diffs, start_q=0, max_p=1, max_q=2,
    start_P=0, D=0, start_Q=0, max_P=0, max_D=0, max_Q=0,
    seasonal=False, error_action='warn', trace=True, supress_warnings=True, stepwise=True,
    random_state=20, n_fits=50)

with open('arima.pkl', 'wb') as pkl:
    pickle.dump(arima_model, pkl)