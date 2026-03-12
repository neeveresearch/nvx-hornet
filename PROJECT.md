# PROJECT.md — nvx-hornet

## What Is This?

Imagine you're building a financial trading system, or a real-time logistics platform, or any application where messages need to fly between services at blistering speed with zero tolerance for data loss. That's the world nvx-hornet lives in.

**nvx-hornet** is a framework for building **Topic-Oriented Applications (TOAs)** — a pattern where your application logic is organized entirely around message topics and channels rather than traditional request/response endpoints. Think of it as a highly opinionated, ultra-low-latency microservices framework built on top of the N5 Rumi platform's Asynchronous Event Processor (AEP) engine.

The name "hornet" is apt — it's small (~6,000 lines of core code), fast, and focused.

## The Big Idea: Topic-Oriented Architecture

Most web developers think in terms of HTTP endpoints: "POST to /orders to create an order." TOA flips this: instead of endpoints, you have **channels** carrying typed messages, and your application subscribes to topics on those channels.

Here's an analogy: imagine a post office (the AEP engine) where every letter (message) has a very specific address (topic). Your application doesn't go to the post office to ask "got any mail?" — instead, the post office has a direct pneumatic tube to your desk for exactly the topics you care about. That's what nvx-hornet wires up for you.

The framework handles:
1. **Declaring** which message types your app sends and receives (via XML service definitions)
2. **Wiring** those declarations into actual messaging channels at startup
3. **Routing** messages to your handlers based on topic resolution
4. **Transacting** — ensuring messages are processed atomically
5. **Clustering** — replicating state across HA backup nodes

## Technical Architecture

### The Two Modules

```
nvx-hornet/
├── nvx-hornet/          # The framework itself
└── nvx-hornet-hk2/      # Optional DI plugin for HK2
```

The core module is self-contained. The HK2 module is a plugin for teams that want enterprise dependency injection (think: "Spring-lite for N5 apps"). Most of the interesting stuff lives in the core.

### How a TOA Application Starts Up

When a `TopicOrientedApplication` boots, here's what happens behind the scenes:

1. **Service Discovery**: The framework calls `ServiceDefinitionLocator` to find all x-tsml XML files on the classpath. These files are the "contract" — they declare what message types exist and which channels carry them.

2. **Schema Parsing**: JAXB-generated classes parse the XML into a typed service model. The schema (`x-tsml.xsd`) defines the grammar.

3. **Channel Wiring**: For each declared channel, the framework creates actual messaging infrastructure — subscribing to topics, setting up QoS, applying filters. This is where the SPI interfaces like `ChannelQosProvider` and `ChannelFilterProvider` kick in.

4. **Topic Resolution Setup**: If channels use dynamic topics (e.g., `ORDERS_${region}_${priority}`), the framework sets up `TopicResolver` instances that extract key values from messages at runtime. This is the secret sauce for high-performance routing without hardcoded topic strings.

5. **Engine Start**: The AEP engine fires up, and your message handlers start receiving messages.

### The Message Flow

```
External System
    │
    ▼
[AEP Engine] ──topic resolution──▶ [Channel] ──▶ [Your Handler Method]
    │                                                    │
    │                                              (process message)
    │                                                    │
    ▼                                                    ▼
[Transaction Context] ◀──────── [MessageSender.sendMessage()]
    │
    ▼
[Outbound Channel] ──▶ External System
```

Everything within a single message handler invocation is transactional. If you receive a message, do some work, and send two response messages, either all of it commits or none of it does.

### The SPI Layer — Extension Without Modification

The framework is designed around the Open/Closed Principle. Rather than subclassing or overriding, you implement small focused interfaces:

- **`TopicResolver<T>`** — "Given this message, what's the topic key?" This is where you'd put logic like "route orders for region=US to the US channel."
- **`ChannelQosProvider`** — "What quality of service does this channel need?" (guaranteed delivery, best-effort, etc.)
- **`ChannelFilterProvider`** — "Should I even process this message?" Filtering before it hits your handler.
- **`ManagedObjectLocator`** — "How do I find beans?" The default implementation does classpath scanning; the HK2 module provides a richer alternative.

These SPIs are discovered via the `ManagedObjectLocator`, which means you just annotate a class with `@Managed` and implement the interface — the framework finds and wires it automatically.

### Threading Model — A Critical Detail

This is important and easy to get wrong:

- **Message handlers are single-threaded.** The AEP engine calls your handler on its dispatch thread. Don't block, don't spawn threads from handlers, don't do I/O.
- **`MessageInjector` is explicitly thread-safe.** If you have a background thread that needs to push a message into the engine, this is how. It's the one sanctioned way to cross the thread boundary.
- **Delayed Acknowledgment** is for when you genuinely need async processing. It lets you defer the "I'm done processing this message" signal to a later time. But there's a catch: it only works with stateless (non-store) engines. If you're doing event sourcing with state replication, delayed ack is off the table.

### Code Generation Pipeline

Two code generators run during the Maven build:

1. **JAXB from XSD**: The `x-tsml.xsd` schema → Java classes for parsing service definition XML. This means the XML service definitions are strongly typed at compile time.

2. **ADM from model XML**: Message model files (in `src/main/models/` and `src/test/models/`) → Java message classes. These generated classes have zero-garbage-friendly APIs using `XString` for string fields, which matters enormously in low-latency scenarios where GC pauses are unacceptable.

## Technologies & Why They Were Chosen

| Technology | Why |
|---|---|
| **nvx-rumi (AEP Engine)** | The Rumi platform's core — provides the actual messaging, clustering, and event sourcing infrastructure (successor to nvx-talon used in 1.x) |
| **JAXB** | XML schema → Java. Gives compile-time type safety for service definitions |
| **javax.inject (JSR-330)** | Standard DI annotations, no framework lock-in |
| **HK2** | Oracle's lightweight DI container — used in the optional module for teams wanting richer DI than classpath scanning |
| **ADM (Advanced Data Model)** | N5's own message serialization — optimized for zero-copy, zero-garbage messaging |
| **Java 8** | Target runtime. The codebase uses pre-module-system Java |

## Lessons and Pitfalls

### 1. Dynamic Topic Resolution Is Powerful but Tricky

The `${field}_${field}` substitution in channel keys is elegant, but if a message arrives with a null field that's part of the topic key, you'll get routing failures that are hard to debug. The `ChannelInitialKeyResolutionTableProvider` SPI exists to pre-populate expected key combinations — use it defensively.

### 2. The Single-Threaded Handler Contract Is Sacred

Coming from a web framework background, your instinct might be to spin up a thread pool in a message handler. Don't. The AEP engine's performance guarantees depend on the single-threaded model. If you need async work, use `MessageInjector` to post results back. The `DelayedAckController` exists for the cases where this is genuinely needed, but it's restricted to stateless engines for good reason — you can't replay async side effects during HA failover.

### 3. Service Definition XML Is the Source of Truth

Don't try to programmatically create channels or wire up topics. The x-tsml XML files are the authoritative declaration of your service's messaging contract. The framework parses them at startup and wires everything. If something isn't in the XML, it doesn't exist to the framework.

### 4. Zero-Garbage APIs Exist for a Reason

You'll notice many interfaces have two variants — one taking `String` and one taking `XString`. The `XString` variants avoid object allocation on the hot path. In a system processing millions of messages per second, the difference between allocating a String and reusing an XString buffer is the difference between smooth latency and GC-induced spikes. Use the `XString` variants in performance-critical paths.

### 5. Test Harness Design

The test infrastructure (`SingleAppToaServer`, `AbstractToaTest`) embeds a full Rumi platform instance. Tests aren't mocking the messaging layer — they're running real message flows through real channels. This is slower than mocked tests but catches integration issues that mocks would miss (like incorrect topic resolution or serialization bugs). The `target/testbed` forked working directory keeps test state isolated.

### 6. The HK2 Module Is Optional by Design

If you're building a simple TOA app, the default `ManagedObjectLocator` (classpath scanning) is fine. HK2 adds value when you have complex dependency graphs, need scoping (request scope, singleton, etc.), or want to use Binders for modular configuration. Don't reach for it unless you need it.

## How Good Engineers Think About This Codebase

1. **Small surface area, deep extension points.** The core framework is ~25 classes. But those classes expose carefully designed SPIs that let you customize almost everything without touching framework code. This is the essence of framework design — make the common case trivial and the uncommon case possible.

2. **XML as configuration, not code.** The x-tsml service definitions keep messaging topology declarative and separate from business logic. This means you can reason about message flow by reading XML, and reason about business logic by reading Java. Neither pollutes the other.

3. **Performance is a feature, not an afterthought.** The zero-garbage APIs, the single-threaded model, the `XString` variants — these aren't premature optimization. In the domain this framework targets (financial systems, real-time data processing), microsecond latency matters. The architecture makes performance constraints explicit rather than hoping for the best.

4. **The framework trusts its platform.** nvx-hornet doesn't try to abstract away the AEP engine — it embraces it. The `TopicOrientedApplication` interface exposes engine lifecycle hooks directly. This tight coupling to the platform is intentional: the framework is a productivity layer over the platform, not a platform-agnostic abstraction.
