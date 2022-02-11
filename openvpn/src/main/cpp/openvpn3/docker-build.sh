#!/bin/sh

# build ovpn3-core with system-provided mbedtls and lz4 on various linux distros

docker build -f dockerfiles/Dockerfile.debian -t deb .
docker run -it deb

docker build -f dockerfiles/Dockerfile.ubu -t ubu .
docker run -it ubu

docker build -f dockerfiles/Dockerfile.centos -t cnt .
docker run -it cnt
