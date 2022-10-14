# SAVE Cloud deployment configuration
## Components
SAVE Cloud contains the following microservices:
* backend: REST API for DB
* test-preprocessor: clones projects for test and discovers tests
* orchestrator: moderates distributed execution of tests, feeds new batches of tests to a set of agents
save-cloud uses MySQL as a database. Liquibase (via gradle plugin) is used for schema initialization and migration.

## Building
* Prerequisites: some components require additional system packages. See [save-agent](../save-agent/README.md) description for details.
  save-frontend requires node.js installation.
* To build the project and run all tests, execute `./gradlew build`.
* For deployment, all microservices are packaged as docker images with the version based on latest git tag and latest commit hash, if there are commits after tag.

Deployment is performed on server via docker swarm or locally via docker-compose. See detailed information below.

## Server deployment
* Server should run Linux and support docker swarm and gvisor runtime. Ideally, kernel 5.+ is required.
* [reverse-proxy.conf](reverse-proxy.conf) is a configuration for Nginx to act as a reverse proxy for save-cloud. It should be 
  copied into `/etc/nginx/sites-available`.
* Gvisor should be installed and runsc runtime should be available for docker. See [installation guide](https://gvisor.dev/docs/user_guide/install/) for details.
  A different runtime can be specified with `orchestrator.docker.runtime` property in orchestrator.
* Ensure that docker daemon is running and that docker is in swarm mode.
* Secrets should be added to the swarm as well as to `$HOME/secrets` file.
* If custom SSL certificates are used, they should be installed on the server and added into JDK's truststore inside images. See section below for details.
* Loki logging driver should be added to docker installation: [instruction](https://grafana.com/docs/loki/latest/clients/docker-driver/#installing)
* Pull new changes to the server and run `./gradlew -Psave.profile=prod -PdockerNetwork=save_default deployDockerStack`.
  * If you wish to deploy save-cloud, that is not present in docker registry (e.g. to deploy from a branch), run `./gradlew -Psave.profile=prod buildAndDeployDockerStack` instead.
  * If you would like to use `docker-compose.override.yaml`, add `-PuseOverride=true` to the execution of tasks above.
    This file is configured to be read from `$HOME/configs`; you can use the one from the repository as an example.
* [`docker-compose.yaml`](../docker-compose.yaml) is configured so that all services use Loki for logging
  and configuration files from `~/configs`, which are copied from `save-deploy` during gradle build.

## Override configuration per server
If you wish to customize services configuration externally (i.e. leaving docker images intact), this is possible via additional properties files.
In [docker-compose.yaml](../docker-compose.yaml) all services have `/home/saveu/configs/<service name>` directory mounted. If it contains
`application.properties` file, it will override config from default `application.properties`.

## Running behind proxy
If save-cloud is running behind proxy, docker daemon should be configured to use proxy. See [docker docs](https://docs.docker.com/network/proxy/).
Additionally, use `/home/saveu/configs/orchestrator/application.properties` to add two flags to `apt-get`:
```properties
orchestrator.aptExtraFlags=-o Acquire::http::proxy="http://host.docker.internal:3128" -o Acquire::https::proxy="http://host.docker.internal:3128"
```
Proxy URLs will be resolved from inside the container.

## Custom SSL certificates
If custom SSL certificates are used, they should be installed on the server and added into JDK's truststore inside images.
One way of adding them into JDK is to mount them in docker-compose.yaml and then override default command:
```yaml
preprocessor:
  volume:
    - '/path/to/certs/cert.cer:/home/cnb/cert.cer'
  entrypoint: /bin/bash
  command: -c 'find /layers -name jre -type d -exec {}/bin/keytool -keystore {}/lib/security/cacerts -storepass changeit -noprompt -trustcacerts -importcert -alias <cert-alias> -file /home/cnb/cert.cer \; && /cnb/process/web'
```

## Database
The service is designed to work with MySQL database. Migrations are applied with liquibase. They expect event scheduler to be enabled on the DB.

## Enabling api-gateway with external OAuth providers
In the file `/home/saveu/configs/gateway/application.properties` the following properties should be provided:
* `spring.security.oauth2.client.provider.<provider name>.issuer-uri`
* `spring.security.oauth2.client.registration.<provider name>.client-id`
* `spring.security.oauth2.client.registration.<provider name>.client-secret`
  
## Local deployment
Usually, not the whole stack is required for development. Application logic is performed by save-backend, save-orchestrator and save-preprocessor, so most time you'll need those three.
* Ensure that docker daemon is running and docker-compose is installed.
  * If running on a system without Unix socket connection to the Docker Daemon (e.g. with Docker for Windows), docker daemon should have HTTP
    port enabled. Then, `docker-tcp` profile should be enabled for orchestrator.
* To make things easier, add line `save.profile=dev` to `gradle.properties`. This will make project version `SNAPSHOT` instead of timestamp-based suffix and allow caching of gradle tasks.
* Run `./gradlew deployLocal -Psave.profile=dev` to start the database and run three microservices (backend, preprocessor and orchestrator) with Docker Compose.
  Run `./gradlew -Psave.profile=dev :save-frontend:run` to start save-frontend using webpack-dev-server, requests to REST API will be
  proxied as configured in [dev-server.js](../save-frontend/webpack.config.d/dev-server.js).
* For developing most part of platform's logic, the above will be enough. If local testing of authentication flow is required, however,
  `api-gateway` can be run locally together with [dex](https://github.com/dexidp/dex) OAuth2 server. In order to do so, run 
  `docker compose up -d dex` and then start `api-gateway` with `dev` profile enabled. Using [`application-dev.yaml`](../api-gateway/src/main/resources/application-dev.yml)
  one can connect gateway with dev build of frontend running with webpack by changing `gateway.frontend.url`.

### Using OAuth with a local deployment

 * When the default [`dev-server.js`](../save-frontend/webpack.config.d/dev-server.js)
   is used, the front-end is expected to communicate directly with the back-end, 
   omitting any gateway. When enabling OAuth, make sure the gateway is contacted
   instead:
 
   * `context`: add `/sec/**`, `/oauth2/**`, and `/login/oauth2/**` to the list;
   * `target`: change to [`http://localhost:5300`](http://localhost:5300) (the
     default gateway URL); 
   * `onProxyReq`: drop the entire callback, since both headers (`Authorization`
     and `X-Authorization-Source`) will be set by the gateway now (the gateway
     acts as a reverse proxy);
   * `bypass`: drop the entire callback.

   The resulting `dev-server.js` should look like this:
   ```javascript
   config.devServer = Object.assign(
       {},
       config.devServer || {},
       {
         proxy: [
           {
             context: ["/api/**", "/sec/**", "/oauth2/**", "/login/oauth2/**", "**.ico", "**.png"],
             target: 'http://localhost:5300',
             logLevel: 'debug',
           }
         ]
       }
   )
   ```
 * Avoid potential name conflicts between local users (those authenticated using
   _HTTP Basic Auth_) and users created via an external _OAuth_ provider. For
   example, if you have a local user named `torvalds`, don't try to authenticate
   as a [_GitHub_ user with the same name](https://github.com/torvalds).

#### _Dex_-specific notes

 * In order to run _Dex_, you need a `build/docker-compose.yaml` file generated.
   This is done by running  
   ```bash
   ./gradlew generateComposeFile
   ```
 * The YML configuration file, [`dex.dev.yaml`](dex.dev.yaml), has a syntax
   explained [here](https://github.com/dexidp/dex/blob/master/examples/config-dev.yaml)
   and [here](https://github.com/wearearima/spring-boot-dex/blob/master/dex/spring-boot-demo.yaml).
 * More users can be added using a static configuration via `dex.dev.yaml`.
   Essential fields explained:
 
   * `hash`: the `bcrypt` hash of the password string:
     ```bash
     echo 'password' | htpasswd -BinC 16 'user' | cut -d: -f2
     ```
     The `htpasswd` utility is a part of `apache2-utils` package. The maximum
     cost supported by `htpasswd` is **17**. _Dex_, on the other hand, only
     allows values up to **16**.
   * `username`: this is the name of the user from _Dex_ perspective only. Since
     _Dex_ (unlike _GitHub_), provides no means to query user details (i.e.
    it has no _User API_), the auto-generated username in the `user` table will
    initially look like `CiRlOGI3NWFmNC1kMDkzLTRhZjUtODk3NC0xMzZlY2IxMGNiNzcSBWxvY2Fs`
    ([example](../info/img/dex-generated-user-name.png)).
   * `userID`: a version 4 (random) GUID (_DCE 1.1_, _ISO/IEC 11578:1996_), can
     be generated [online](https://www.guidgenerator.com/online-guid-generator.aspx),
     or using [`uuidgen -r`](https://packages.debian.org/uuid-runtime) , `uuid`, or `uuidcdef -u` (Linux),
     or by running
     ```bash
     python3 -c 'import uuid; print(str(uuid.uuid4()))'
     ```

 * For debugging purposes, you may wish to run _Dex_ in the foreground:
   ```bash
   docker compose up dex
   ```

#### _GitHub_-specific notes

 * The `spring.security.oauth2.client.provider.github.user-name-attibute` under
   `application.yml` or `application properties` should be set to `login`. This
   is because in the default configuration (`o.s.s.c.o.c.CommonOAuth2Provider#GITHUB`),
   the numeric `id` field is taken from the JSON response received from
   [api.github.com/user](https://api.github.com/user), and we want the
   publicly-visible `login` value instead. See
   [_GitHub User API_](https://docs.github.com/en/rest/users/users#get-the-authenticated-user)
   for more details.
 * To use _GitHub_ as an _OAuth_ provider, you'll need to create a
   [GitHub OAuth application](https://docs.github.com/en/developers/apps/building-oauth-apps/). 
   Essential fields explained:
 
   * **Client ID**: the unique application id, which will appear in the outgoing
     requests from the gateway to _GitHub_. Configure the gateway accordingly by
     setting the `spring.security.oauth2.client.registration.github.client-id`
     property.
   * **Client secrets**: holds the secret the gateway will use to authenticate
     itself at _GitHub_. Store it in the
     `spring.security.oauth2.client.registration.github.client-secret` property.
   * **Homepage URL**: should be set to your font-end URL, i.e.
     [`http://localhost:8080`](http://localhost:8080).
   * **Authorization callback URL**: holds the URL _GitHub_ will redirect to
     once it successfully authenticates a user. Should be exactly
     [`http://localhost:8080/login/oauth2/code/github`](http://localhost:8080/login/oauth2/code/github).   
   * **Enable Device Flow**: leave enabled.

   The resulting application settings may look like this: [screenshot](../info/img/github-oauth-app-settings.png).

## Local debugging
You can run backend, orchestrator, preprocessor and frontend locally in IDE in debug mode.<br/>
If you run on Windows, dependency `save-agent` is omitted because of problems with linking in cross-compilation.<br/>
To run on Windows you need to compile `save-agent` on WSL and put `saveAgentDistroFilepath` to `%USERPROFILE%\.gradle\gradle.properties` <br/>
For example:

    saveAgentDistroFilepath=file:\\\\\\\\wsl$\\Ubuntu\\home\\username\\projects\\save-cloud\\save-agent\\build\\libs\\save-agent-0.3.0-alpha.0.48+1c1fd41-distribution.jar

If you need to test changes in `save-cli` you can also compile `SNAPSHOT` version of `save-cli` on WSL <br/>
and set `saveCliPath` and `saveCliVersion` in `%USERPROFILE%\.gradle\gradle.properties` <br/>
For example:

    saveCliPath=file:\\\\\\\\wsl$\\Ubuntu\\home\\username\\projects\\save-cli\\save-cli\\build\\bin\\linuxX64\\releaseExecutable
    saveCliVersion=0.4.0-alpha.0.42+78a24a8

the version corresponds to the file `save-0.4.0-alpha.0.42+78a24a8-linuxX64.kexe` <br/>

#### Some workarounds:
If setting `save-agent`'s path in `gradle.properties` didn't help you (something doesn't work on Mac), you still can place all the files from `save-agent-*-distribution.jar` into `save-orchestrator/build/resources/main`.
Moreover, if you use Mac with Apple Silicon, you should run `docker-mac-settings.sh` in order to let docker be available via TCP.
Do not forget to use `mac` profile.

#### Note: 
* This works only if snapshot version of save-core is set in lib.version.toml. 
* If version of save-core is set without '-SNAPSHOT' suffix, then it is considered as release version and downloaded from github.

## Ports allocation
| port | description            |
|------|------------------------|
| 3306 | database (locally)     |
| 5800 | save-backend           |
| 5810 | save-frontend          |
| 5100 | save-orchestrator      |
| 5200 | save-test-preprocessor |
| 5300 | api-gateway            |
 | 5400 | save-sandbox          |
| 9090 | prometheus             |
| 9091 | node_exporter          |
| 9100 | grafana                |

## Secrets
* Liquibase is reading secrets from the secrets file located on the server in the `home` directory.
* PostProcessor is reading secrets for database connection from the docker secrets and fills the spring datasource. (DockerSecretsDatabaseProcessor class)
* api-gateway is a single external-facing component, hence its security is stricter. Actuator endpoints are protected with
basic HTTP security. Access can be further restricted by specifying `gateway.knownActuatorConsumers` in `application.properties`
(if this options is not specified, no check will be performed).

# Server configuration
## Nginx
Nginx is used as a reverse proxy, which allows access from external network to backend and some other services.
File `save-deploy/reverse-proxy.conf` should be copied to `/etc/nginx/sites-available`. Symlink should be created:
`sudo ln -s /etc/nginx/sites-available/reverse-proxy.conf /etc/nginx/sites-enabled/` (or to `/etc/nginx/conf.d` on some distributions).

# Adding a new service
Sometimes it's necessary to create a new service. These steps are required to seamlessly add it to deployment:
* Add it to [docker-compose.yaml](../docker-compose.yaml)
* Add it to task `depoyDockerStack` in [`DockerStackConfiguration.kt`](../buildSrc/src/main/kotlin/com/saveourtool/save/buildutils/DockerStackConfiguration.kt)
  so that config directory is created (if it's another Spring Boot service)
