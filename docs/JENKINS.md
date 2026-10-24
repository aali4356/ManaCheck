# Local Jenkins verification

`Jenkinsfile` runs `./scripts/verify.sh`, the same verification entry point used by Actions. It requires JDK 21, Chrome/Chromium, Docker access, and the Pipeline, Git, and JUnit plugins. It publishes test reports and screenshots even on failure. Missing reports fail the job.

Build the optional local controller image from its small context:

```bash
docker build -t manacheck-jenkins:local ci/jenkins
docker run -d --name manacheck-jenkins --group-add 0 \
  --add-host host.docker.internal:host-gateway \
  -p 127.0.0.1:18080:8080 \
  -v manacheck-jenkins:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -e TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  -e DOCKER_HOST=unix:///var/run/docker.sock \
  manacheck-jenkins:local
```

Open `http://localhost:18080`, complete Jenkins's local setup, and create a **Pipeline from SCM** job pointing at this repository and `Jenkinsfile`. Use one executor. Keep the controller on loopback and use the disposable local Docker runtime; the job can start containers through that socket.

The image supplies Chromium and its matching driver on Linux, including ARM64. Use a Docker client compatible with the daemon. This Mac's bundled 20.10 client could not export an image through its 29.5 daemon; building inside the dedicated Colima VM with the matching client resolved that prerequisite failure.

The first run downloads public Maven dependencies. Tests use an owned PostgreSQL 17 container and fixture decks, without Scryfall or an AI key. The image's LTS/plugin tags resolve at build time; verification receipts record the actual controller and image versions.
