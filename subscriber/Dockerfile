FROM critoma/amd64_u24_noble_ism_security

RUN apt-get update && apt-get install -y \
    openjdk-21-jdk maven \
    openmpi-bin libopenmpi-dev libssl-dev gcc \
    openssh-client snmpd snmp

# SSH setup
RUN mkdir -p /root/.ssh
COPY keys/id_rsa /root/.ssh/id_rsa
COPY keys/id_rsa.pub /root/.ssh/authorized_keys
RUN chmod 700 /root/.ssh && \
    chmod 600 /root/.ssh/id_rsa && \
    echo "StrictHostKeyChecking no" > /root/.ssh/config

# Configure SNMP daemon
RUN echo "rocommunity public" > /etc/snmp/snmpd.conf && \
    echo "agentAddress udp:161" >> /etc/snmp/snmpd.conf

# Compile MPI encryption binary
COPY encrypt.c /app/encrypt.c
RUN mpicc -fopenmp -o /app/encrypt /app/encrypt.c -lssl -lcrypto

# Build Java subscriber
COPY subscriber/pom.xml /app/subscriber/pom.xml
RUN cd /app/subscriber && mvn dependency:go-offline
COPY subscriber/src /app/subscriber/src
RUN cd /app/subscriber && mvn package -DskipTests

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

CMD ["bash", "/entrypoint.sh"]
