FROM critoma/amd64_u24_noble_ism_security
RUN apt-get update && apt-get install -y openjdk-21-jdk supervisor maven curl snmpd snmp && \
      curl -fsSL https://deb.nodesource.com/setup_24.x | bash - && \
      apt-get install -y nodejs

# Configure SNMP daemon
RUN echo "rocommunity public" > /etc/snmp/snmpd.conf && \
    echo "agentAddress udp:161" >> /etc/snmp/snmpd.conf

WORKDIR /app

COPY ./backend/pom.xml ./backend/pom.xml

RUN cd backend && mvn dependency:go-offline

COPY ./backend/src ./backend/src

RUN cd backend && mvn package -DskipTests

COPY ./frontend/package.json ./frontend/package.json
COPY ./frontend/package-lock.json ./frontend/package-lock.json
RUN cd frontend && npm install

COPY ./frontend/src ./frontend/src
COPY ./frontend/public ./frontend/public

COPY ./frontend/index.html ./frontend/index.html
COPY ./frontend/vite.config.ts ./frontend/vite.config.ts
COPY ./frontend/tsconfig.json ./frontend/tsconfig.json
COPY ./frontend/tsconfig.app.json ./frontend/tsconfig.app.json
COPY ./frontend/tsconfig.node.json ./frontend/tsconfig.node.json
COPY ./frontend/env.d.ts ./frontend/env.d.ts
COPY supervisord.conf /etc/supervisor/conf.d/supervisord.conf
EXPOSE 8080 5173
CMD ["/usr/bin/supervisord"]
