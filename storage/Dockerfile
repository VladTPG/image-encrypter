FROM critoma/amd64_u24_noble_ism_security

RUN apt-get update && apt-get install -y mysql-server gnupg curl && \
    curl -fsSL https://deb.nodesource.com/setup_24.x | bash - && \
    apt-get install -y nodejs

RUN curl -fsSL https://www.mongodb.org/static/pgp/server-8.0.asc | \
    gpg --dearmor | tee /usr/share/keyrings/mongodb-server-8.0.gpg > /dev/null

RUN echo "deb [ arch=amd64 signed-by=/usr/share/keyrings/mongodb-server-8.0.gpg ] https://repo.mongodb.org/apt/ubuntu noble/mongodb-org/8.0 multiverse" | \
    tee /etc/apt/sources.list.d/mongodb-org-8.0.list

RUN apt-get update && apt-get install -y mongodb-org

RUN npm install -g express mongoose mysql2

RUN sed -i 's/bind-address\s*=\s*127.0.0.1/bind-address = 0.0.0.0/' /etc/mysql/mysql.conf.d/mysqld.cnf
RUN sed -i 's/bindIp: 127.0.0.1/bindIp: 0.0.0.0/' /etc/mongod.conf

EXPOSE 3306 27017 3000
COPY init_script.sh /init_script.sh
CMD ["bash", "/init_script.sh"]