# Requirements:
#   - The acompaning `build-and-push.sh` must be run from project root.

# Note: Do not use this Dockerfile directly, as the `build-and-push.sh` must do additional prepares
#       before the image can be built.

FROM registry.sztaki.dev:443/application/scala:latest

ENV MEMORY "1"
ENV JAVA_OPTIONS "-Xmx${MEMORY}G"
ENV MAIN_CLASS ""

WORKDIR /root

COPY core-assembly-*.jar /root/

CMD java -server $JAVA_OPTIONS -cp "override-configuration/:*" $MAIN_CLASS