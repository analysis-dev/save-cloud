# Helm chart for save-cloud
This chart will install components of save-cloud application: gateway, backend, orchestrator and preprocessor.
It will also create a Service for an external MySQL database.

api-gateway acts as an entrypoint and svc/gateway is actually a LoadBalancer.

## Prerequisites
* save-backend expects the following secrets to be set under the secret `db-secrets` (`kubectl create secret generic db-secrets <...>`, 
  also see Secrets section in dev profile in [mysql-deployment.yaml](templates/mysql-deployment.yaml) as a reference):
  * `spring.datasource.username`
  * `spring.datasource.password`
  * `spring.datasource.backend-url`
  * `spring.datasource.sandbox-url`
  
  These secrets are then mounted under the path specified as `DATABASE_SECRETS_PATH` environment variable.

  For example, for minikube and dev profile run `kubectl --context=minikube --namespace=save-cloud create secret generic db-secrets --from_literal=spring.datasource.username=<...> <...>`
* `kubectl create secret generic oauth-credentials ...` this secret should contain properties recognizable by spring security OAuth;
  it's used by api-gateway.

## Versions of the chart
On each commit that contains changes in the directory with save-cloud chart, the chart is packaged and published to 
[saveourtool's repo at ghcr.io](https://github.com/saveourtool/save-cloud/pkgs/container/save-cloud). To use the pre-built chart,
use the following notation in Helm CLI: `oci://ghcr.io/saveourtool/save-cloud --version=<latest version from the link above>`.

To test changes in chart that are not yet available in the upstream, one can package the chart locally:
```bash
$ cd save-cloud-charts
$ helm package ./save-cloud
Successfully packaged chart and saved it to: .../save-cloud-0.1.0.tgz
```

Versioning of the chart is not bound to versioning of individual services. Value `dockerTag` is used as a default tag for images,
it can be overridden for any service, e.g. `backend.dockerTag` will apply to `save-backend` only.
Here and further, values refer to default values from `values.yaml` or values from any other value files or values set from
command line using `--set` flag.

## Local deployment
* Install minikube: https://minikube.sigs.k8s.io/docs/start/
* Environment should be prepared:
  ```bash
  minikube ssh
  docker@minikube:~$ for d in repos volumes resources; do sudo mkdir -p /tmp/save/$d && sudo chown -R 1000:1000 /tmp/save/$d; done
  ```
* Images can be built directly in Minikube. To point image building tasks to Minikube's Docker Daemon,
  run `docker port minikube` and look for mapping of the port 2376; run `minikube docker-env` and look for `$Env:DOCKER_CERT_PATH`.
  Add the following properties (actual values may be different) into `gradle.properties`:
  ```properties
  build.docker.host=https://127.0.0.1:49156
  build.docker.tls-verify=true
  build.docker.cert-path=<path-to-user-home>/.minikube/certs
  ```
* (Required only once) Install Helm chart using `values-minikube.yaml`: 
  ```bash
  $ helm install save-cloud save-cloud-0.1.0.tgz --namespace save-cloud --values values-minikube.yaml <any other value files and/or --set flags>
  ```
* (On consecutive deployments) Upgrade an existing Helm release:
  ```bash
  $ helm upgrade save-cloud save-cloud-0.1.0.tgz --namespace save-cloud --values values-minikube.yaml <any other value files and/or --set flags>
  ```
* Database migrations can be run by setting value `mysql.migrations.enabled` to `true` (no additional setup, migrations
  are executed by init container, but may be too slow with constant recreations of backend/sandbox pods)
  or port 3306 of mysql pod can be forwarded and `liquibaseUpdate` can be executed manually. If needed, don't forget that
  JDBC URL for liquibase in dev profile is read from `application-dev.properties` value `dev.datasource.url`.
