# Helm chart for save-cloud
This chart will install components of save-cloud application: gateway, backend, orchestrator and preprocessor.
It will also create a Service for an external MySQL database.

api-gateway acts as an entrypoint and svc/gateway is actually a LoadBalancer.

## Prerequisites
* save-backend expects the following secrets to be set under the secret `db-secrets` (`kubectl create secret generic db-secrets <...>`, 
  also see Secrets section in dev profile in [mysql-deployment.yaml](templates/mysql-deployment.yaml) as a reference): 
  * `spring.datasource.url`
  * `spring.datasource.username`
  * `spring.datasource.password`
  
  These secrets are then mounted under the path specified as `DATABASE_SECRETS_PATH` environment variable.

  For example, for minikube and dev profile run `kubectl --context=minikube --namespace=save-cloud create secret generic db-secrets --from_literal=spring.datasource.username=<...> <...>`
* `kubectl create secret generic oauth-credentials ...` this secret should contain properties recognizable by spring security OAuth

## Build and deploy
```bash
$ helm package ./save-cloud
Successfully packaged chart and saved it to: .../save-cloud-0.1.0.tgz
$ helm install save-cloud save-cloud-0.1.0.tgz --namespace save-cloud
```

## Local deployment
* Install minikube: https://minikube.sigs.k8s.io/docs/start/
* Database can be configured with `mysql.migrations.enabled=true` or port 3306 of mysql pod can be forwarded
  and `liquibaseUpdate` can be executed manually. If needed, don't forget that JDBC URL for liquibase in dev profile
  is read from `application-dev.properties` value `dev.datasource.url`.
* Environment should be prepared:
  ```bash
  minikube ssh
  docker@minikube:~$ for d in repos volumes resources; do sudo mkdir -p /tmp/save/$d && sudo chown -R 1000:1000 /tmp/save/$d; done
  ```
* Install Helm chart using `values-minikube.yaml`: 
  ```bash
  $ helm install save-cloud save-cloud-0.1.0.tgz --namespace save-cloud --values values-minikube.yaml
  ```
