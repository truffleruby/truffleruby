# Installing `libssl`

Since TruffleRuby 33.0.0, TruffleRuby ships with its own `libssl` and no longer needs a system `libssl`.

The only dependency that must be installed on the system is the CA certificates.

### Fedora-based: RHEL, Oracle Linux, etc

```bash
sudo dnf install ca-certificates
```

### Debian-based: Ubuntu, etc

```bash
sudo apt-get install ca-certificates
```

### macOS

On macOS, the certificates from Apple cannot be used as-is because they are in a different format.
Converting them to libssl format means taking a snapshot of the certificates, which can become problematic as those get outdated.
So the best is to install a CA certificates package on macOS.

#### Homebrew

We recommend installing `ca-certificates` via [Homebrew](https://brew.sh).

```bash
brew install ca-certificates
```

#### MacPorts

MacPorts should also work but is not actively tested.

```bash
sudo port install curl-ca-bundle
```
