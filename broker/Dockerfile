FROM critoma/amd64_u24_noble_ism_security

RUN apt-get update && apt-get install -y rabbitmq-server snmpd snmp

# Configure SNMP daemon
RUN echo "rocommunity public" > /etc/snmp/snmpd.conf && \
    echo "agentAddress udp:161" >> /etc/snmp/snmpd.conf

EXPOSE 5672 15672

COPY rabbitmq.conf /etc/rabbitmq/rabbitmq.conf

CMD ["bash", "-c", "snmpd -Lo -C -c /etc/snmp/snmpd.conf 2>/dev/null; rabbitmq-plugins enable rabbitmq_management && rabbitmq-server"]
