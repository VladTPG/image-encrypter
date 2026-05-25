FROM critoma/amd64_u24_noble_ism_security

RUN apt-get update && apt-get install -y mysql-server gnupg curl snmpd snmp && \
    curl -fsSL https://deb.nodesource.com/setup_24.x | bash - && \
    apt-get install -y nodejs

RUN curl -fsSL https://www.mongodb.org/static/pgp/server-8.0.asc | \
    gpg --dearmor | tee /usr/share/keyrings/mongodb-server-8.0.gpg > /dev/null

RUN echo "deb [ arch=amd64 signed-by=/usr/share/keyrings/mongodb-server-8.0.gpg ] https://repo.mongodb.org/apt/ubuntu noble/mongodb-org/8.0 multiverse" | \
    tee /etc/apt/sources.list.d/mongodb-org-8.0.list

RUN apt-get update && apt-get install -y mongodb-org

COPY package.json /app/package.json
RUN cd /app && npm install

RUN sed -i 's/bind-address\s*=\s*127.0.0.1/bind-address = 0.0.0.0/' /etc/mysql/mysql.conf.d/mysqld.cnf
RUN sed -i 's/bindIp: 127.0.0.1/bindIp: 0.0.0.0/' /etc/mongod.conf

# Configure SNMP daemon
RUN echo "rocommunity public" > /etc/snmp/snmpd.conf && \
    echo "agentAddress udp:161" >> /etc/snmp/snmpd.conf

COPY server.js /app/server.js
COPY snmp-collector.js /app/snmp-collector.js

EXPOSE 3306 27017 3000
COPY init_script.sh /init_script.sh
CMD ["bash", "/init_script.sh"]
