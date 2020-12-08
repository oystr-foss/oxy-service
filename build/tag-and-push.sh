#!/bin/bash

if [ -z "${DOCKER_TAG}" ]; then
    echo "Missing DOCKER_TAG environment variable"
    exit 1
fi

project=docker.pkg.github.com/oystr-foss/oxy-service
name=oxy-service

echo "Tagging image with ${DOCKER_TAG}" &&
  docker tag "${name}":"${DOCKER_TAG}" "${project}"/"${name}":"${DOCKER_TAG}"

echo "Pushing image to remote repo" &&
  docker -- push "${project}"/"${name}":"${DOCKER_TAG}"

echo "Done"
