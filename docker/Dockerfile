FROM zzvara/spark:3.1.1-SNAPSHOT

ARG module
ARG version

USER root

RUN mkdir /opt/spark/configuration || exit 1
RUN mkdir /opt/spark/dependencies || exit 1

COPY ${module}-assembly-${version}.jar /opt/spark/dependencies/${module}-assembly-${version}.jar
RUN ln -s /opt/spark/dependencies/${module}-assembly-${version}.jar /opt/spark/dependencies/application.jar

ENTRYPOINT [ "/opt/entrypoint.sh" ]
