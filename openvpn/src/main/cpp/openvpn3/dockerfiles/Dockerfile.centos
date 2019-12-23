FROM centos/devtoolset-7-toolchain-centos7

USER 0
RUN yum -y update && yum -y install epel-release && \
    yum -y install -y mbedtls-devel lz4-devel git wget perl-Digest-SHA make

ADD . /ovpn3/core

ENV O3 /ovpn3/
ENV DEP_DIR /ovpn3/deps
ENV DL /ovpn3/dl

CMD mkdir $DEP_DIR && mkdir $DL && \
    /ovpn3/core/scripts/linux/build-all && \
    cd $O3/core/test/ovpncli && \
    ECHO=1 PROF=linux ASIO=1 MTLS_SYS=1 LZ4_SYS=1 NOSSL=1 $O3/core/scripts/build cli && \
    cd $O3/core/test/ssl && \
    ECHO=1 PROF=linux ASIO=1 MTLS_SYS=1 LZ4_SYS=1 NOSSL=1 $O3/core/scripts/build proto && \
    ./proto
