dropbox-sync
============

A custom two-way [Dropbox](https://www.dropbox.com/developers) syncing application designed specifically for [Docker](https://www.docker.com/get-docker) environments.

You can find an auto-deployed image using the command [`docker pull electroid/dropbox`](https://hub.docker.com/r/electroid/dropbox/).

# Problem

Currently, in order to deploy Dropbox into a Docker environment, you need to manually configure the entire host machine. Now, you can provision a Dropbox container with *just* an access token.

# Build

You will need a recent version of [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) as well as [Maven](http://maven.apache.org) installed on your computer. To build the program, just run `mvn clean install` and the jar should appear at `target/dropbox-sync-1.0-SNAPSHOT.jar`. After building is complete, you may also create a [Dockerfile](https://docs.docker.com/engine/reference/builder/) using the command `docker build -t dropbox .`

If you want your image to auto deploy you can set up a [container builder](https://cloud.google.com/container-builder) to listen to changes in your Github repository. When setting up, you must reference the `build.yml` file in the repository, so the build program knows how to create your `grc.io/$PROJECT_ID/dropbox` image.

# Setup

Each container requires three environment variables to be defined:
1. DROPBOX_FOLDER_LOCAL  *(optional, "/dropbox")*
2. DROPBOX_FOLDER_REMOTE *(optional, "")*
3. DROPBOX_OAUTH_TOKEN   *(required)*

# Deployment

Here is an example of running a solo container using **Docker:**
```
docker run --volume=your_own_host_path:$DROPBOX_FOLDER_LOCAL gcr.io/$PROJECT_ID/dropbox:master
```

Here is an example of a StatefulSet deployment using **Kubernetes:**
```
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: dropbox
spec:
  serviceName: dropbox
  selector:
    matchLabels:
      role: dropbox
  template:
    metadata:
      labels:
        role: dropbox
    spec:
      containers:
        - name: dropbox-sidecar
          image: gcr.io/$PROJECT_ID/dropbox:master
          envFrom:
            - secretRef:
                name: dropbox-secret
          resources:
            requests:
              cpu: 100m
              memory: 200Mi
            limits:
              cpu: 100m
              memory: 200Mi
          volumeMounts:
            - name: dropbox
              mountPath: /dropbox:rw
      volumes:
        - name: dropbox
          hostPath:
            path: /dropbox
```
