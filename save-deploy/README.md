# SAVE Cloud deployment configuration
## Components
SAVE Cloud contains the following microservices:
* backend: REST API for DB
* test-preprocessor: clones projects for test and discovers tests
* orchestrator: moderates distributed execution of tests, feeds new batches of tests to a set of agents

## Building
* Prerequisites: some components require additional system packages. See [save-agent](../save-agent/README.md) description for details.
  save-frontend requires node.js installation.
* To build the project and run all tests, execute `./gradlew build`.
* For deployment, all microservices are packaged as docker images with the version based on latest git tag and latest commit hash, if there are commits after tag.
To build release version after you create git tag, make sure to run gradle with `-Preckon.stage=final`.

Deployment is performed on server via docker swarm or locally via docker-compose. See detailed information below.

## Server deployment
* Gvisor should be installed and runsc runtime should be available for docker. See [installation guide](https://gvisor.dev/docs/user_guide/install/) for details.
* Ensure that docker daemon is running and that docker is in swarm mode.
* Pull new changes to the server and run `./gradlew deployDockerStack`.

## Local deployment
* Ensure that docker daemon is running and docker-compose is installed.
* Run `./gradlew deployLocal -Pprofile=dev` to start only some components.

#### Note:
If a snapshot version of save-cli is required (i.e., the one which is not available on GitHub releases), then it can be
manually placed in `save-orchestrator/build/resources/main` before build, and it's version should be provided via `-PsaveCliVersion=...` when executing gradle.

## Ports allocation
| port | description |
| ---- | ----------- |
| 3306 | database (locally) |
| 5000 | save-backend |
| 5100 | save-orchestrator |
| 5200 | save-test-preprocessor |
| 6000 | local docker registry (not used currently) |
| 9090 | prometheus |
| 9091 | node_exporter |
| 9100 | grafana |

## Secrets
* Liquibase is reading secrets from the secrets file located on the server in the `home` directory.
* PostProcessor is reading secrets for database connection from the docker secrets and fills the spring datasource. (DockerSecretsDatabaseProcessor class)

# Server configuration
## Nginx
Nginx is used as a reverse proxy, which allows access from external network to backend and some other services.
File `save-deploy/reverse-proxy.conf` should be copied to `/etc/nginx/sites-available`.