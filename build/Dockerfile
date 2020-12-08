FROM oystrcombr/oystr-base-java:latest
MAINTAINER Luan Melo - luan.sm50@gmail.com - luan.melo@oystr.com.br

LABEL oystr.container.type=oystr-service

ADD bin  /opt/oystr/service/bin
ADD conf /opt/oystr/service/conf
ADD lib  /opt/oystr/service/lib

RUN mkdir -p /etc/service/oystr-service
ADD run      /etc/service/oystr-service/run
