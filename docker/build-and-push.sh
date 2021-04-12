#!/bin/bash

set -x -o errexit -o pipefail -o noclobber -o nounset

! getopt --test > /dev/null
if [[ ${PIPESTATUS[0]} -ne 4 ]]; then
    echo 'I’m sorry, `getopt --test` failed in this environment!'
    exit 1
fi

SHORT_OPTIONS=m
LONG_OPTIONS=module:

! PARSED=$(getopt --options=$SHORT_OPTIONS --longoptions=$LONG_OPTIONS --name "$0" -- "$@")
if [[ ${PIPESTATUS[0]} -ne 0 ]]; then
    exit 2
fi

eval set -- "$PARSED"

module=-
while true; do
    case "$1" in
        -m|--module)
            module="$2"
            shift 2
            ;;
        --)
            shift
            break
            ;;
        *)
            echo "Programming error!"
            exit 3
            ;;
    esac
done

cp version.sbt version
sed -i 's/ThisBuild\ \/\ version\ \:\=\ \"//g' version
sed -i 's/\"//g' version

export BUILD_VERSION=$(<version)
export NAMESPACE="zzvara"
export APPLICATION="spark-disqus"
export BUILD_DIRECTORY="docker"
export REGISTRY="registry.hub.docker.com"

cp -r $module/target/scala-2.12/$module-assembly-$BUILD_VERSION.jar $BUILD_DIRECTORY/$module-assembly-$BUILD_VERSION.jar || exit 1

docker build --pull \
             -t $NAMESPACE/$APPLICATION:$BUILD_VERSION \
             -t $NAMESPACE/$APPLICATION:latest \
             -f $BUILD_DIRECTORY/Dockerfile \
             --build-arg module=$module \
             --build-arg version=$BUILD_VERSION \
             $BUILD_DIRECTORY || exit 1

docker tag $NAMESPACE/$APPLICATION:$BUILD_VERSION $REGISTRY/$NAMESPACE/$APPLICATION:$BUILD_VERSION || exit 1
docker tag $NAMESPACE/$APPLICATION:latest $REGISTRY/$NAMESPACE/$APPLICATION:latest || exit 1

docker push $REGISTRY/$NAMESPACE/$APPLICATION:$BUILD_VERSION || exit 1
docker push $REGISTRY/$NAMESPACE/$APPLICATION:latest || exit 1
