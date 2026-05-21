FROM critoma/amd64_u24_noble_ism_security
RUN apt-get update && apt-get install -y openjdk-21-jdk supervisor maven curl && \        
      curl -fsSL https://deb.nodesource.com/setup_24.x | bash - && \                        
      apt-get install -y nodejs    
WORKDIR /app

COPY ./backend/pom.xml ./backend/pom.xml

RUN cd backend && mvn dependency:go-offline

COPY ./backend/src ./backend/src

RUN cd backend && mvn package -DskipTests

COPY ./frontend/package.json ./frontend/package.json
RUN cd frontend && npm install

COPY ./frontend/src ./frontend/src

COPY ./frontend/index.html ./frontend/index.html
COPY ./frontend/vite.config.ts ./frontend/vite.config.ts
COPY supervisord.conf /etc/supervisor/conf.d/supervisord.conf
EXPOSE 8080 5173
CMD ["/usr/bin/supervisord"]

