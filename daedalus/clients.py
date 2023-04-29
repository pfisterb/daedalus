import requests
import pandas as pd
import numpy as np
from urllib.parse import urljoin
from kubernetes import client, config

class PrometheusClient:
    def __init__(self, api_url: str):
        self.http = requests.Session()
        self.api_url = api_url

    def query_range(self, query: str, start: int, end: int, step: int) -> pd.DataFrame:
        params = {'query': query, 'start': start, 'end': end, 'step': step, 'timeout': 120}

        resp = self.http.get(urljoin(self.api_url, 'api/v1/query_range'), params=params, timeout=120)
        response = resp.json()

        if response['status'] != 'success':
            raise RuntimeError('{errorType}: {error}'.format_map(response))

        data = response['data']

        result_type = data['resultType']
        if result_type == 'matrix':
            return pd.DataFrame({
                # column name is pod, default to query
                r['metric'].get('pod', query):
                    pd.Series((np.float64(v[1]) for v in r['values']),
                    index=(pd.Timestamp(v[0], unit='s') for v in r['values']))
                for r in data['result']})
        else:
            raise ValueError('Unknown type: {}'.format(result_type))

class KubernetesClient:
    def __init__(self):
        config.load_kube_config()
        self.k8s_client = client.AppsV1Api()

    def rescale(self, name: str, namespace: str, scaleout: int):

        self.k8s_client.patch_namespaced_deployment_scale(name, namespace, {'spec': {'replicas': scaleout}})

        deployment = self.k8s_client.read_namespaced_deployment_status(name, namespace)
        # wait until pods in new scaleout are running/terminated
        while deployment.status.ready_replicas != scaleout:
            deployment = self.k8s_client.read_namespaced_deployment_status(name, namespace)