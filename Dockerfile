FROM openjdk:8-jdk-slim-buster

ENV ANDROID_COMPILE_SDK "30"
ENV ANDROID_BUILD_TOOLS "30.0.2"
ENV ANDROID_SDK_TOOLS "30.0.2"
ENV ANDROID_NDK_VERSION "21.3.6528147"

RUN apt update && apt-get install -y \
  swig \
  netbase \
  connect-proxy \
  bash \
  python3 \
  git \
  jq \
  rsync \
  gnupg \
  dirmngr \
  bzip2 \
  unzip \
  xz-utils \
  tar \
  lib32stdc++6 \
  lib32z1 \
  libtool \
  gettext \
  gperf \
  pkg-config \
  automake \
  gcc \
  wget \
  bison \
  flex \
  gperf \
  curl \
  openssh-client \
  rubygems

# Install Firebase cli
RUN echo "deb https://packages.cloud.google.com/apt cloud-sdk main" | tee -a /etc/apt/sources.list.d/google-cloud-sdk.list
RUN curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -
RUN apt-get update && apt-get install google-cloud-sdk -y

# Install appetize cli
RUN gem install appetize-cli --no-document

# Because the alias is not there out of the box
RUN ln -s /usr/bin/python3 /usr/bin/python

WORKDIR /root

# IF we want swig 4
# Add this so we can install swig 4.0 instead of the old 3.0.12 from 3 years old
# Cannot group it before has it creates an issue with libc6 during the install :/
#RUN echo 'deb http://http.us.debian.org/debian/ testing non-free contrib main' >> /etc/apt/sources.list && \
#  apt-get update && apt-get install -y swig

RUN mkdir android-sdk-linux && \
  curl \
    --silent \
    https://dl.google.com/android/repository/commandlinetools-linux-6609375_latest.zip \
    --output android-sdk.zip && \
  unzip -d android-sdk-linux/cmdline-tools android-sdk.zip && \
  rm android-sdk.zip && \
  export ANDROID_HOME="${HOME}/android-sdk-linux" && \
  export ANDROID_CLI="${ANDROID_HOME}/cmdline-tools" && \
  yes | "${ANDROID_CLI}/tools/bin/sdkmanager" \
    --sdk_root="${ANDROID_HOME}" \
    "platform-tools" "platforms;android-${ANDROID_COMPILE_SDK}" >/dev/null && \
  yes | "${ANDROID_CLI}/tools/bin/sdkmanager" \
    --sdk_root="${ANDROID_HOME}" \
    "build-tools;${ANDROID_BUILD_TOOLS}" \
    "build-tools;29.0.2" \
    "cmake;3.10.2.4988404" \
    "cmake;3.6.4111459" \
    "extras;android;m2repository" \
    "ndk;${ANDROID_NDK_VERSION}" >/dev/null

ENV ANDROID_HOME /root/android-sdk-linux
ENV ANDROID_SDK_ROOT /root/android-sdk-linux
ENV ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/${ANDROID_NDK_VERSION}
ENV ANDROID_CLI $ANDROID_HOME/cmdline-tools
ENV PATH ${ANDROID_HOME}/tools:${ANDROID_NDK_HOME}:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/cmdline-tools/tools/bin:$PATH

COPY entrypoint /usr/local/bin

ENTRYPOINT ["/usr/local/bin/entrypoint"]
