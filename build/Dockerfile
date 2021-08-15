FROM oystrcombr/oystr-base-java:latest
MAINTAINER Rafael Silvério - rafael.silverio.it@gmail.com - rafael.silverio@oystr.com.br

LABEL oystr.container.type=oystr-service

ADD bin  /opt/oystr/service/bin
ADD conf /opt/oystr/service/conf
ADD lib  /opt/oystr/service/lib

RUN mkdir -p /etc/service/oystr-service
ADD run      /etc/service/oystr-service/run

HEALTHCHECK --interval=5m --timeout=10s --start-period=30s CMD curl --fail http://localhost:10000/services || exit 1
CMD ["bash", "/etc/service/oystr-service/run"]
