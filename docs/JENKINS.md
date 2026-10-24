# Local Jenkins verification

`Jenkinsfile` runs `./scripts/verify.sh`, the same verification entry point used by Actions. It requires JDK 21, Chrome/Chromium, Docker access, and the Pipeline, Git, and JUnit plugins. It publishes test reports and screenshots even on failure. Missing reports fail the job.

Build the optional local controller image from its small context:

```bash
docker build -t manacheck-jenkins:local ci/jenkins
MANACHECK_DOCKER_GID="$(docker run --rm --entrypoint stat \
  -v /var/run/docker.sock:/var/run/docker.sock manacheck-jenkins:local \
  -c '%g' /var/run/docker.sock)"
docker run -d --name manacheck-jenkins --group-add "$MANACHECK_DOCKER_GID" \
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

For a job pinned to a commit SHA, disable **Lightweight checkout**. The local
socket group was 991; the command above discovers it rather than assuming root's
group. The image installs `docker-cli`; Debian's `docker.io` package supplies
the daemon without the CLI when recommended packages are disabled.

The image supplies Chromium and its matching driver on Linux, including ARM64. Use a Docker client compatible with the daemon. This Mac's bundled 20.10 client could not export an image through its 29.5 daemon; building inside the dedicated Colima VM with the matching client resolved that prerequisite failure.

The first run downloads public Maven dependencies. Tests use an owned PostgreSQL 17 container and fixture decks, without Scryfall or an AI key. The image's LTS/plugin tags resolve at build time; verification receipts record the actual controller and image versions.

## Observed run

Local build **3** passed all **98** checks with zero failures or skips in **57.363 seconds**, at commit `0bbe5570139025e6815d66d043a67b9cfa5a2e72`. JUnit and test artifacts were archived. [The receipt](evidence/jenkins-verification.json) records the actual controller/image and the two earlier setup failures, which executed no tests. The local authenticated controller is available at `http://localhost:18080`.
