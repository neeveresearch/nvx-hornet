# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

nvx-hornet is a high-performance message-driven framework for building **Topic-Oriented Applications (TOAs)** on the Neeve X Platform. It provides event-driven message processing via `AepEngine`, automatic topic/channel resolution, clustering/HA support, and transaction context management.

## Build Commands

```bash
mvn clean install                        # Build all modules
mvn test                                 # Run all tests
mvn test -pl nvx-hornet -Dtest=ToaMessagingTest  # Run a single test class
```

Java 8 target. Maven 3.5.4+. Custom Neeve repositories are declared in the POM for nvx-talon dependencies.

### Line endings — `MessageSender.java` is CRLF

`nvx-hornet/src/main/java/com/neeve/toa/MessageSender.java` is stored with **CRLF** line terminators in git on both `1.16` and `develop`. Any scripted edit that reads and rewrites it in text mode will normalize it to LF and turn a five-line javadoc tweak into a 188-line whole-file diff. Before committing a change to that file, run `file` on it (or check `git diff --stat`) and confirm it still matches the committed line endings.

(Fixes land on the `1.16` maintenance branch and are forward-ported to `develop`, so the same file has to be edited carefully twice.)

## Module Structure

- **nvx-hornet/** — Core framework. Service definitions (x-tsml XML), message routing, topic resolution, delayed acknowledgment, and the `TopicOrientedApplication` lifecycle.
- **nvx-hornet-hk2/** — Optional HK2 dependency injection plugin (`HK2ManagedObjectLocator`).

## Architecture

### Message Flow
Applications declare services via **x-tsml XML** files specifying message types, channels, and routing keys. At runtime, `TopicOrientedApplication` wires channels to an `AepEngine` which handles async event processing. Messages flow through typed channels with dynamic topic resolution (placeholders like `${field1}_${field2}` in channel keys).

### Service Model Parsing
`ToaService.unmarshal` cannot hand `AdmXMLParser` a classpath resource, so `resolveMessageModelFile()` copies each declared message model out of the classpath to a temp file (`xmd*` in `java.io.tmpdir`). That copy is deleted in a `finally` immediately after `AdmXMLParser.parse()` returns; do not fall back to relying on `deleteOnExit()`, which never fires for a container killed by SIGKILL/OOM (TOA-134). Deleting eagerly is safe because `parse(File)` passes a null `modelsDir`, so `resolveImportModel` never takes its filesystem-relative branch and resolves imports through the classloader only. The `deleteOnExit()` inside `UtlFile.copyToTempFile` stays as a backstop.

### Key Interfaces (com.neeve.toa)
- `TopicOrientedApplication` — Main application interface and lifecycle
- `MessageSender` — Send messages to configured channels. The five `sendMessage(String channelName, ...)` overloads bypass channel resolution and hand the send straight to `AepMessageSender`, which has no notion of `receiveOnly`; each one therefore calls `checkNotReceiveOnly(channelName)` (throws `ToaException`) against the receiveOnly channel names `TopicOrientedApplication` collects during service configuration (TOA-132). Two details when touching that set: the key is `ToaServiceChannel.getName()`, the service-prefixed name the channel is registered under on the bus, which is exactly what these overloads take; and the names must be gathered in the per-service channel loop that visits *every* declared channel, not the later bus-descriptor loop, which skips unmapped and off-bus channels (a receiveOnly channel is quite likely to be unmapped).
- `MessageInjector` — Thread-safe message injection into the engine
- `EngineClock` — HA-consistent time for event sourcing

### SPI Extension Points (com.neeve.toa.spi)
- `ServiceDefinitionLocator` — Locate service definition XMLs
- `TopicResolver<T>` — Resolve dynamic topic keys from messages
- `ChannelQosProvider`, `ChannelFilterProvider`, `ChannelJoinProvider` — Per-channel customization
- `ManagedObjectLocator` — DI bean discovery (default impl or HK2)

### Code Generation
- **jaxb2-maven-plugin** generates Java classes from `x-tsml.xsd` (service definition schema)
- **nvx-adm-maven-plugin** generates message classes from model XML files in `src/main/models/` and `src/test/models/`

### Threading Model
Engine message handlers run single-threaded. `MessageInjector` is explicitly thread-safe for external injection. The delayed acknowledgment feature (`opt/impl/`) enables async processing outside handler threads but is restricted to non-store (stateless) engines.

## Testing

Tests live in `nvx-hornet/src/test/`. Key base classes:
- `AbstractToaTest` — Base with logging/comparison utilities
- `SingleAppToaServer` — Embedded server harness

Tests fork with working directory `target/testbed`. Service definitions for tests are in `src/test/resources/` (e.g., `forwarderService.xml`, `receiverService.xml`). Test message models are in `src/test/models/`.
