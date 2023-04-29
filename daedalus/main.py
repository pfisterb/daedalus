from execution_manager import ExecutionManager
import time

# logging
import logging
import sys
logging.basicConfig(stream=sys.stdout, format="%(asctime)s:%(levelname)s:%(name)s: %(message)s", level=logging.INFO)

if __name__ == '__main__':

    manager = ExecutionManager()

    # deploy DSP system(s) via helm chart
    manager.experiment_manager.deploy()

    # wait for containers to come online
    time.sleep(500)

    # run daedalus
    manager.mape()

    # undeploy DSP systems
    # manager.experiment_manager.undeploy()

