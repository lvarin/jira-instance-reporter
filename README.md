# Instance Reporter

This is a simple plugin to report number of users, projects and issues. It provides a gadget and a simple API.

## Build

In order to build this plugin, you must install first the [Atlassian SDK](https://developer.atlassian.com/server/framework/atlassian-sdk/set-up-the-atlassian-plugin-sdk-and-build-a-project/), you can also use a docker image like [agonzale/atlassian-plugin-sdk](https://hub.docker.com/r/agonzale/atlassian-plugin-sdk). Then is as simple as to run:

```
make
```

This can also be done using a docker image like [agonzale/atlassian-plugin-sdk](https://hub.docker.com/r/agonzale/atlassian-plugin-sdk), from the folder this plugin is:

```
docker run -it -v $PWD:/root/src/ agonzale/atlassian-plugin-sdk
```

and once inside the docker image:

```
cd /root/src/
atlas-mvn package
```

The JAR file with the compiled plug in will be in the `target` directory.
