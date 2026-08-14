# P2P File Sharing App

Simple Java Swing implementation for the CSE471 makeup term project.

## Features

- No centralized server.
- UDP limited-scope flooding discovery with shared-secret isolation.
- TCP hello protocol so open ports can be checked with tools such as `nmap`.
- Runs on a user-selected TCP port.
- GUI setup screen, main screen, Files/Help menus, Connect/Disconnect/Exit.
- Recursive shared-folder scan with optional excluded child folders.
- File identity is based on SHA-256 and size, so the same file can be recognized even if peers use different file names.
- 512 KB chunk transfer.
- Downloads chunks from multiple peers when more than one peer has the same file.
- Active transfer table shows bytes and percentage.
- Downloaded files are stored in the shared folder.

## How to Run in Eclipse

1. Open Eclipse.
2. Use `File -> Import -> Existing Projects into Workspace`.
3. Select this project folder.
4. Run `p2p.Main`.

## How to Run from Terminal

```bash
javac -d bin $(find src -name "*.java")
java -cp bin p2p.Main
```

## How to Run with Docker

Docker mode runs headless P2P nodes in a custom bridge network named `p2pnet`:

```bash
docker compose up --build
```

The Docker setup starts two sharing peers and one downloader peer. See `DOCKER.md` for the full test steps.

## Basic Test

Run the application on two computers in the same LAN, or on two VMs/containers in the same network.

1. Choose the same shared secret on both nodes.
2. Choose different shared folders.
3. Use any free TCP port, for example `5001` and `5002`.
4. Put comma-separated child folder names in `Excluded Child Folders` when some subfolders should not be shared.
5. Click Start or use `Files -> Connect`.
6. Shared files should appear in the Files Found table.
7. Select a file and press Download, or double-click a file to download it.

The TCP service answers a simple hello line, so a found open port can be identified:

```text
HELLO
```

with:

```text
P2P471 HELLO_SERVICE <hostName> <port>
```

Peers that know the same shared secret use:

```text
HELLO <secretHash>
```

with:

```text
P2P471 HELLO_OK <nodeId> <hostName> <port>
```
