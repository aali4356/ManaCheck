FROM jenkins/jenkins:lts-jdk21
USER root
RUN apt-get update && apt-get install -y --no-install-recommends chromium chromium-driver python3 ca-certificates git curl docker-cli && rm -rf /var/lib/apt/lists/*
USER jenkins
RUN jenkins-plugin-cli --plugins workflow-aggregator git junit pipeline-model-definition
ENV CHROME_BIN=/usr/bin/chromium
