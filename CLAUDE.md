# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

nvx-hornet is a high-performance message-driven framework for building **Topic-Oriented Applications (TOAs)** on N5's Rumi platform. It provides event-driven message processing via `AepEngine`, automatic topic/channel resolution, clustering/HA support, and transaction context management.

## Build Commands

```bash
mvn -Pneeve clean install                                    # Build all modules (tests skipped by default)
mvn -Pneeve -Dnv.test.groups=all clean install               # Build and run ALL tests
mvn -Pneeve -Dnv.test.groups=all test -pl nvx-hornet -Dtest=ServiceModelTest  # Run a single test class
```

**Important:** Tests are skipped by default on this branch. You must pass `-Dnv.test.groups=all` to actually execute them.

Java 8 target. The `-Pneeve` profile activates custom Neeve Maven repositories for nvx-rumi dependencies.

## Module Structure

- **nvx-hornet/** — Core framework. Service definitions (x-tsml XML), message routing, topic resolution, delayed acknowledgment, and the `TopicOrientedApplication` lifecycle.
- **nvx-hornet-hk2/** — Optional HK2 dependency injection plugin (`HK2ManagedObjectLocator`).

## Architecture

### Message Flow
Applications declare services via **x-tsml XML** files specifying message types, channels, and routing keys. At runtime, `TopicOrientedApplication` wires channels to an `AepEngine` which handles async event processing. Messages flow through typed channels with dynamic topic resolution (placeholders like `${field1}_${field2}` in channel keys).

### Key Interfaces (com.neeve.toa)
- `TopicOrientedApplication` — Main application interface and lifecycle
- `MessageSender` — Send messages to configured channels
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

## Branch Notes

- **develop** (2.0-SNAPSHOT): Uses nvx-rumi. Tests require `-Dnv.test.groups=all`. Copyright headers use N5 Technologies.
- **1.16** (1.16-SNAPSHOT): Uses nvx-talon. Tests run by default without the groups flag. Copyright headers use Neeve Research.
