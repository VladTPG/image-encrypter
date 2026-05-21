FROM critoma/amd64_u24_noble_ism_security

RUN apt-get update && apt-get install -y rabbitmq-server

EXPOSE 5672 15672

COPY rabbitmq.conf /etc/rabbitmq/rabbitmq.conf

CMD ["bash", "-c", "rabbitmq-plugins enable rabbitmq_management && rabbitmq-server"]