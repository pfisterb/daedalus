import subprocess
import time
from clients import PrometheusClient

class ExperimentManager:
    def __init__(self, config):
        self.config = config
        self.prometheus = PrometheusClient(self.config.prometheus_url)
        self.experiment_start = int(time.time())

    # for convenience
    def deploy(self):
        self.deploy_single_flink()
        # self.deploy_flink()
        # self.deploy_single_kafka()
        # self.deploy_kafka()

    def undeploy(self):
        subprocess.run(["helm", "uninstall", "-n", self.config.namespace, self.config.deployment_name])

        subprocess.run(["helm", "uninstall", "-n", self.config.namespace, "flink-reactive-80"])
        subprocess.run(["helm", "uninstall", "-n", self.config.namespace, "flink-reactive-85"])
        subprocess.run(["helm", "uninstall", "-n", self.config.namespace, "flink-static"])

        subprocess.run(["helm", "uninstall", "-n", self.config.namespace, "kstreams-reactive-80"])
        subprocess.run(["helm", "uninstall", "-n", self.config.namespace, "kstreams-reactive-60"])
        subprocess.run(["helm", "uninstall", "-n", self.config.namespace, "kstreams-static"])

    def deploy_single_flink(self):
        # deploy flink for daedalus
        subprocess.run(["helm", "install", "-n", self.config.namespace, self.config.deployment_name, "../helm_charts/charts/flink",
                        "--set", f"taskmanager.replicas={self.config.initial_replicas}",
                        "--set", f"name={self.config.deployment_name}",
                        "--set", f"jobClassname={self.config.job_classname}",
                        "--set", "hpa.enabled=false"])

    def deploy_flink(self):
        # deploy flink for daedalus
        subprocess.run(["helm", "install", "-n", self.config.namespace, self.config.deployment_name, "../helm_charts/charts/flink",
                        "--set", f"taskmanager.replicas={self.config.initial_replicas}",
                        "--set", f"name={self.config.deployment_name}",
                        "--set", f"jobClassname={self.config.job_classname}",
                        "--set", "hpa.enabled=false"])

        # deploy flink reactive HPA 80
        subprocess.run(["helm", "install", "-n", self.config.namespace, "flink-reactive-80", "../helm_charts/charts/flink",
                        "--set", f"taskmanager.replicas={self.config.initial_replicas}",
                        "--set", 'name=flink-reactive-80',
                        "--set", f"jobClassname={self.config.job_classname}",
                        "--set", "hpa.enabled=true",
                        "--set", "hpa.targetUtilization=80",
                        "--set", f"hpa.maxScaleout={self.config.max_scaleout}"])

        # deploy flink reactive HPA 85
        subprocess.run(["helm", "install", "-n", self.config.namespace, "flink-reactive-85", "../helm_charts/charts/flink",
                        "--set", f"taskmanager.replicas={self.config.initial_replicas}",
                        "--set", 'name=flink-reactive-85',
                        "--set", f"jobClassname={self.config.job_classname}",
                        "--set", "hpa.enabled=true",
                        "--set", "hpa.targetUtilization=85",
                        "--set", f"hpa.maxScaleout={self.config.max_scaleout}"])

        # deploy flink with static max scaleout
        subprocess.run(["helm", "install", "-n", self.config.namespace, "flink-static", "../helm_charts/charts/flink",
                        "--set", f"taskmanager.replicas={self.config.initial_replicas}",
                        "--set", 'name=flink-static',
                        "--set", f"jobClassname={self.config.job_classname}",
                        "--set", "hpa.enabled=false",
                        "--set", f"taskmanager.replicas=12"])

    def deploy_single_kafka(self):
        # deploy kafka for daedalus
        subprocess.run(["helm", "install", "-n", self.config.namespace, self.config.deployment_name, "../helm_charts/charts/kafka-streams",
                        "--set", f"replicas={self.config.initial_replicas}",
                        "--set", f"name={self.config.deployment_name}",
                        "--set", f"jobClassname={self.config.job_classname}",
                        "--set", "hpa.enabled=false"])

    def deploy_kafka(self):
        # deploy kafka for daedalus
        subprocess.run(["helm", "install", "-n", self.config.namespace, self.config.deployment_name, "../helm_charts/charts/kafka-streams",
                        "--set", f"replicas={self.config.initial_replicas}",
                        "--set", f"name={self.config.deployment_name}",
                        "--set", f"jobClassname={self.config.job_classname}",
                        "--set", "hpa.enabled=false"])

        # deploy flink reactive
        subprocess.run(["helm", "install", "-n", self.config.namespace, "kstreams-reactive-80", "../helm_charts/charts/kafka-streams",
                        "--set", f"replicas={self.config.initial_replicas}",
                        "--set", 'name=kstreams-reactive-80',
                        "--set", f"jobClassname={self.config.job_classname}",
                        "--set", "hpa.enabled=true",
                        "--set", "hpa.targetUtilization=80",
                        "--set", f"hpa.maxScaleout={self.config.max_scaleout}"])

        # deploy flink reactive
        subprocess.run(["helm", "install", "-n", self.config.namespace, "kstreams-reactive-60", "../helm_charts/charts/kafka-streams",
                        "--set", f"replicas={self.config.initial_replicas}",
                        "--set", 'name=kstreams-reactive-60',
                        "--set", f"jobClassname={self.config.job_classname}",
                        "--set", "hpa.enabled=true",
                        "--set", "hpa.targetUtilization=60",
                        "--set", f"hpa.maxScaleout={self.config.max_scaleout}"])

        # deploy flink with static max scaleout
        subprocess.run(["helm", "install", "-n", self.config.namespace, "kstreams-static", "../helm_charts/charts/kafka-streams",
                        "--set", 'name=kstreams-static',
                        "--set", f"jobClassname={self.config.job_classname}",
                        "--set", "hpa.enabled=false",
                        "--set", f"replicas={self.config.max_scaleout}"])
