FROM quay.io/debezium/connect:3.0

# ES Sink Connector 다운로드
USER root
RUN mkdir -p /kafka/connect/kafka-connect-elasticsearch && \
    cd /tmp && \
    curl -sL "https://d2p6pa21dvn84.cloudfront.net/api/plugins/confluentinc/kafka-connect-elasticsearch/versions/15.0.2/confluentinc-kafka-connect-elasticsearch-15.0.2.zip" -o es-connector.zip && \
    unzip -q es-connector.zip -d /kafka/connect/kafka-connect-elasticsearch && \
    rm es-connector.zip
USER kafka
