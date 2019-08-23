# JIRA Instance Reporter gadget

This is a simple gadget plugin for JIRA to report number of users, projects, and issues. It provides a gadget and a simple API.

## Build

In order to build this plugin, you must install first the [Atlassian SDK](https://developer.atlassian.com/server/framework/atlassian-sdk/set-up-the-atlassian-plugin-sdk-and-build-a-project/), you can also use a docker image like [agonzale/atlassian-plugin-sdk](https://hub.docker.com/r/agonzale/atlassian-plugin-sdk). Then is as simple as to run:

```
docker run -it -v $PWD:/root/src -w /root/src/ agonzale/atlassian-plugin-sdk make
```

Docker needs to download all the dependencies before building, it can take a while. The JAR file with the compiled plug in will be in the `target` directory.

## Manual install

Once the jar file is created, the plugin can be installed by login as an administrator in JIRA, go to 'Manage Apps', and click in 'Upload app'. After that, the gadget will be available to be added to a dashboard.
