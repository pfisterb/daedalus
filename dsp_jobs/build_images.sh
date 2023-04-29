docker build -f docker/Dockerfile.flink -t pfisterb/flink:1.16.0  .
docker push pfisterb/flink:1.16.0

docker build -f docker/Dockerfile.kafka -t pfisterb/kafka-streams:3.2.0  .
docker push pfisterb/kafka-streams:3.2.0

# docker build -f docker/Dockerfile.spark -t pfisterb/spark:3.3.2  .
# docker push pfisterb/spark:3.3.2

docker build -f docker/Dockerfile.ysb -t pfisterb/ysb-generator:1.0  .
docker push pfisterb/ysb-generator:1.0

docker build -f docker/Dockerfile.wc -t pfisterb/wc-generator:1.0  .
docker push pfisterb/wc-generator:1.0

docker build -f docker/Dockerfile.tm -t pfisterb/tm-generator:1.0  .
docker push pfisterb/tm-generator:1.0