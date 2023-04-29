class Config:
    def __init__(self):

        ### daedalus configs ###
        self.loop_time = 60
        self.max_scaleout = 18
        self.initial_replicas = 1  # number of instances to deploy initially
        self.target_recovery_time = 600  # in seconds

        self.namespace = "daedalus"
        self.prometheus_url = "http://130.149.248.64:30816"

        self.dsp_framework = "flink"  # or "kafka"

        ### job configs ###
        self.consumer_topic = "ad-events"
        self.job_classname = "de.tu_berlin.dos.arm.yahoo_streaming_benchmark.processor.Run"

        # self.consumer_topic = "traffic-monitoring-events"
        # self.job_classname = "de.tu_berlin.dos.arm.traffic_monitoring.processor.Run"

        # self.consumer_topic = "word-events"
        # self.job_classname = "de.tu_berlin.dos.arm.wordcount.processor.WordCount"

        ### flink configs ###
        self.deployment_name = "flink"
        self.rescale_name = "flink-taskmanager"  # in this case, different from deployment
        self.source_operator = "Source:_KafkaSource"
        self.latency_quantile = "0.95"

        ### kafka configs ###
        # self.deployment_name = "kafka-streams"
        # self.rescale_name = "kafka-streams"
        # self.source_operator = "0_.*"  # regex for source operators
        # self.daedalus_queries = self.queries(self.monitoring_name)


        ### for monitoring
        self.monitoring_name = f"{self.namespace}/{self.deployment_name}-metrics"
        self.daedalus_queries = self.queries(self.monitoring_name)

    def queries(self, monitoring_name):

        queries = {
            "cpu": f"avg_over_time(flink_taskmanager_Status_JVM_CPU_Load{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\"}}[1m])",
            "throughput": f"sum(flink_taskmanager_job_task_numRecordsOutPerSecond{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\",task_name=\"{self.source_operator}\"}}) by (pod)",
            "total_throughput": f"sum(flink_taskmanager_job_task_numRecordsOutPerSecond{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\",task_name=\"{self.source_operator}\"}})",
            "latency": f"avg(flink_taskmanager_job_task_operator_eventTimeLag{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\",quantile=\"{self.latency_quantile}\"}}) by (pod)",
            "total_latency": f"avg(flink_taskmanager_job_task_operator_eventTimeLag{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\",quantile=\"{self.latency_quantile}\"}})",
            "workload": f"sum(kafka_server_brokertopicmetrics_messagesinpersec_oneminuterate{{namespace=\"{self.namespace}\",topic=\"{self.consumer_topic}\"}})",
            "parallelism": f"flink_jobmanager_numRegisteredTaskManagers{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\"}}",
            "uptime": f"flink_jobmanager_job_uptime{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\"}}",
            "consumer_lag": f"sum(flink_taskmanager_job_task_operator_KafkaSourceReader_KafkaConsumer_records_lag_max{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\"}})"
        }

        kafka_queries = {
            "cpu": f"avg_over_time(java_lang_operatingsystem_processcpuload{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\"}}[1m])",
            "throughput": f"sum(rate(kafka_streams_stream_task_metrics_process_total{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\",task_id=~\"{self.source_operator}\"}}[1m])) by (pod)",
            "total_throughput": f"sum(rate(kafka_streams_stream_task_metrics_process_total{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\",task_id=~\"{self.source_operator}\"}}[1m]))",
            "latency": f"avg(kafka_streams_stream_processor_node_metrics_record_e2e_latency_avg{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\",task_id=~\"1_.*\"}}) by (pod)",
            "total_latency": f"avg(kafka_streams_stream_processor_node_metrics_record_e2e_latency_avg{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\",task_id=~\"1_.*\"}})",
            "workload": f"sum(kafka_server_brokertopicmetrics_messagesinpersec_oneminuterate{{namespace=\"{self.namespace}\",topic=\"{self.consumer_topic}\"}})",
            "parallelism": f"sum(kafka_streams_stream_metrics_alive_stream_threads{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\"}})",
            # "uptime": f"min(java_lang_Runtime_Uptime{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\"}})",
            "consumer_lag": f"sum(kafka_consumer_consumer_fetch_manager_metrics_records_lag_max{{namespace=\"{self.namespace}\",job=\"{monitoring_name}\",topic=\"{self.consumer_topic}\"}}>0)"
        }

        if self.dsp_framework == "kafka":
            queries = kafka_queries

        # return queries
        return queries