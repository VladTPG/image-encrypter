FROM critoma/amd64_u24_noble_ism_security

RUN apt-get update && apt-get install -y \
    openmpi-bin libopenmpi-dev libssl-dev gcc \
    openssh-server snmpd snmp

# SSH setup
RUN mkdir -p /var/run/sshd /root/.ssh
COPY keys/id_rsa /root/.ssh/id_rsa
COPY keys/id_rsa.pub /root/.ssh/authorized_keys
RUN chmod 700 /root/.ssh && \
    chmod 600 /root/.ssh/id_rsa /root/.ssh/authorized_keys && \
    echo "StrictHostKeyChecking no" > /root/.ssh/config && \
    echo "PermitRootLogin yes" >> /etc/ssh/sshd_config

# Configure SNMP daemon
RUN echo "rocommunity public" > /etc/snmp/snmpd.conf && \
    echo "agentAddress udp:161" >> /etc/snmp/snmpd.conf

# Compile MPI encryption binary
COPY encrypt.c /app/encrypt.c
RUN mpicc -fopenmp -o /app/encrypt /app/encrypt.c -lssl -lcrypto

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 22
CMD ["bash", "/entrypoint.sh"]
