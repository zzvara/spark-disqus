#!/bin/bash

# This helps on Bamboo to have actual script outputs.
set -x

export BUILD_VERSION=$(<version)
export NAMESPACE="spark"
export APPLICATION="disqus"
export REGISTRY="registry.sztaki.dev:443"

docker build -t $NAMESPACE/$APPLICATION:$BUILD_VERSION -t $NAMESPACE/$APPLICATION:latest -f $APPLICATION/src/main/resources/Dockerfile $APPLICATION/target/scala-2.12/

docker tag $NAMESPACE/$APPLICATION:$BUILD_VERSION $REGISTRY/$NAMESPACE/$APPLICATION:$BUILD_VERSION
docker tag $NAMESPACE/$APPLICATION:latest $REGISTRY/$NAMESPACE/$APPLICATION:latest

docker push $REGISTRY/$NAMESPACE/$APPLICATION:$BUILD_VERSION
docker push $REGISTRY/$NAMESPACE/$APPLICATION:latest