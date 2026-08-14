# Docker Test

This project includes a headless Docker mode for testing the P2P network inside a containerized network.
The Swing GUI is still available for desktop use through `p2p.Main`.

## Start Three Containers

```bash
docker compose up --build
```

If Docker Hub is slow in the lab, the base image can be changed at build time:

```bash
BASE_IMAGE=eclipse-temurin:21-jdk docker compose up --build
```

The compose file starts:

- `peer1` on TCP port `5001`
- `peer2` on TCP port `5002`
- `downloader` on TCP port `5003`

All three containers use the same shared secret, `cse471`, and join the same Docker bridge network.
`peer1` and `peer2` share the same file content under different names. The `downloader` container auto-downloads remote files.

## Check The Docker Network

The compose file creates a custom bridge network named `p2pnet`, matching the Docker network approach used for container-to-container communication.

```bash
docker network ls
docker network inspect p2pnet
```

You should see `p2p-peer1`, `p2p-peer2`, and `p2p-downloader` attached to the same bridge network.

The shared folders are mounted as host-backed volumes under `docker-data/`, so downloaded files stay on the host even after containers stop.

## Check The Download

In another terminal:

```bash
ls -l docker-data/downloader
```

You should see the downloaded file after the containers discover each other.

## Check The Hello Protocol

After the containers are running:

```bash
printf "HELLO\n" | nc localhost 5001
```

Expected response:

```text
P2P471 HELLO_SERVICE <hostName> 5001
```

## Stop

```bash
docker compose down
```
