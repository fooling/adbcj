# Asynchronous Database Connectivity in Java (ADBCJ)

**English** | [中文](#中文文档)

ADBCJ lets you talk to a relational database in a fully **asynchronous, non-blocking** fashion. The API is inspired by JDBC, but every call is asynchronous: no thread ever sits idle waiting for the database to answer.

The core idea of ADBCJ is **multiplexing many requests over a single connection**: independent statements from multiple requesters are **pipelined** — written to the wire back-to-back without waiting for the previous round trip, with responses correlated back to their callers as they arrive. One connection can have dozens of requests in flight at once (the per-connection queue, 64 by default, is exactly this multiplexing window). Depending on the workload this can yield a significant performance gain.

ADBCJ is intended as a **low-level foundation**. It is written in plain Java so that other JVM languages (Scala, Kotlin, Groovy, …) and higher-level libraries can build on top of it.

> **Key message.** The lasting contribution of this project is a working *proof that the database link layer can be multiplexed*: many logical requesters can share one physical connection, with requests pipelined on the wire and responses routed back to their callers. The programming model on top — callbacks, `CompletableFuture`, Reactive Streams, or virtual threads — is an **orthogonal** choice. Transport-level multiplexing does not compete with any of them; it composes with all of them.

## Features

- Asynchronous, non-blocking versions of the familiar JDBC operations: `executeQuery`, `executeUpdate`, `prepareQuery`, `prepareUpdate`, transactions (`beginTransaction` / `commit` / `rollback`)
- Two programming styles for every operation:
  - **Java 8 `CompletableFuture`** — compose operations with `thenCompose`, `allOf`, etc.
  - **Low-level `DbCallback`** — plug ADBCJ into your own future/async framework
- **Native async drivers** built on Netty for **MySQL** and **H2** (no JDBC underneath)
- A **JDBC bridge driver** that wraps any blocking JDBC driver behind the async API
- Request **pipelining**: independent statements are sent immediately, without waiting for previous round trips
- Built-in **connection pool** (optional, off by default)
- **Back-pressure protection**: a per-connection pending-request queue limit (default 64)
- **Debuggable async stack traces**: optionally capture the caller's stack at every entry point so errors point back to your code
- A **TCK** (technology compatibility kit) that every driver must pass

## Core Design: Single-Connection Request Multiplexing

With JDBC, a connection is a *serial* resource: one statement occupies it for the full round trip, so N concurrent statements require N pooled connections, and each statement pays full network latency. ADBCJ inverts this. A connection holds a queue of in-flight requests; new statements are written to the socket immediately and the driver's Netty event loop matches responses back to the pending callbacks in protocol order.

Here is how it works on the wire, using the MySQL driver (classic protocol) as the concrete example:

```
  requesters                          ADBCJ MySqlConnection                    MySQL server
  (any thread / future chain)                                               (classic protocol)

  +-------------+   Q1
  | requester A |-------+
  +-------------+       |        FIFO requestQueue (in-flight window <= 64)
  +-------------+   Q2  |        +------+------+------+ - - +
  | requester B |-------+------> |  Q1  |  Q2  |  Q3  |     |  callbacks parked here,
  +-------------+       |        +------+------+------+ - - +  in send order
  +-------------+   Q3  |           |
  | requester C |-------+           |  written to the socket immediately,
  +-------------+                   v  no waiting for earlier round trips

                       ========== single TCP connection ==========
       --> [COM_QUERY Q1][COM_QUERY Q2][COM_QUERY Q3] ------------->  executes strictly
               (pipelined: ~1 round trip for all 3)                   in arrival order
       <-- [Result   Q1 ][Result   Q2 ][Result   Q3 ] <-------------

                    |
                    v
   The Netty event loop decodes each response and pops the queue head
   (requestQueue.poll()) to complete the matching caller's callback:

       Result Q1 -> requester A    Result Q2 -> requester B    Result Q3 -> requester C
```

Note the correlation trick: the MySQL classic protocol has **no request IDs** — the server simply answers in the order it received the commands. That is exactly why a FIFO queue is sufficient to route every response back to its requester, and also why responses can never overtake each other on one connection (true out-of-order multiplexing would need a protocol with stream IDs, like HTTP/2 or MySQL X Protocol).

The practical effects:

- **Round-trip cost is amortized**: K independent statements cost roughly one round trip instead of K.
- **Far fewer connections**: many logical requesters share one physical connection, instead of a one-statement-one-connection pool.
- **Back-pressure is explicit**: the in-flight window is bounded (`adbcj.maxQueueLength`, default 64) and overflow fails fast rather than silently overloading the database.

This is the same design that later appeared in PostgreSQL's pipeline mode (libpq 14+) and is the main thing ADBCJ offers that a connection pool cannot.

## ADBCJ vs R2DBC vs Project Loom

Both R2DBC and Project Loom address the same original pain point — threads blocked waiting on the database — but from different angles:

| | ADBCJ | R2DBC | JDBC + Project Loom (virtual threads) |
|---|---|---|---|
| Programming model | `CompletableFuture` / plain callback | Reactive Streams (`Publisher`, usually via Reactor) | Ordinary blocking code on virtual threads |
| Concurrency unit | In-flight request on a shared connection | Subscription per statement | One virtual thread + one pooled connection per statement |
| Single-connection pipelining | **Yes — the core feature** | Driver-dependent, not part of the spec contract | No — JDBC semantics stay serial per connection |
| Back-pressure | Bounded request queue, fail-fast | Demand-based, down to row-level streaming | Implicit (thread blocks); pool size is the limit |
| Result sets | Materialized collections (plus a row-event `ResultHandler`) | Streamed rows with demand control | JDBC `ResultSet` cursors |
| Ecosystem | Minimal, dormant; MySQL + H2 native drivers | Active spec (1.0, 2022), Spring Data backing, many drivers | The Java mainstream since JDK 21 |
| Dependency footprint | Netty + SLF4J | Reactive Streams implementation required | None beyond JDBC |

Put bluntly: **Loom removes the *thread* cost of waiting, but not the *connection and round-trip* cost** — a virtual thread blocked on JDBC still monopolizes its connection for a full round trip per statement. **R2DBC standardizes the reactive programming model**, but per-connection request pipelining is left to individual driver implementations. ADBCJ's remaining distinctive value is precisely the explicit, first-class single-connection multiplexing — with a far simpler programming model than Reactive Streams, at the price of a much smaller ecosystem.

Note that this is not a contest between three rivals. Loom and R2DBC answer the question *"how do I write concurrent database code?"* — a programming-model question. ADBCJ's multiplexing answers *"how do statements travel over the wire?"* — a transport question. The two layers are independent, and the ideal stack would combine them.

## Future Outlook

Honest assessment of where this niche is heading:

- **Oracle's official async JDBC (ADBA) was abandoned in 2019 explicitly in favor of Loom.** The Java mainstream is settling on blocking JDBC + virtual threads; for most CRUD workloads with a warm connection pool, that is now good enough.
- **R2DBC owns the reactive niche** thanks to Spring backing. Competing with it on API breadth is not realistic for this project.
- **Pipelining is the part neither of them gives you.** Databases themselves are moving this way (PostgreSQL pipeline mode, MySQL X Protocol multiplexing), so the most interesting evolution path for ADBCJ-like designs is *not* "async API" but "pipelined transport": e.g. a blocking-looking API for virtual threads that transparently multiplexes statements from many virtual threads onto few physical connections — combining Loom's programming model with ADBCJ's wire efficiency.
- **Project status**: upstream development is dormant (last release 0.9). Treat this codebase as a working reference implementation of async wire protocols (MySQL, H2) and of the multiplexing design — valuable for study and experimentation rather than as a production dependency.

## Modules

| Module | Maven artifact | Description |
|---|---|---|
| `api` | `org.adbcj:adbcj-api` | The driver-independent API and supporting classes |
| `mysql` | `org.adbcj:mysql-async-driver` | Native asynchronous MySQL driver (Netty-based) |
| `h2` | `org.adbcj:h2-async-driver` | Native asynchronous H2 driver (Netty-based) |
| `jdbc` | `org.adbcj:adbcj-jdbc` | Bridge that exposes any JDBC driver through the ADBCJ API |
| `tck` | – | Compatibility test suite run against every driver |
| `adbcj-demo` | – | Runnable tutorials (see below) |

## Getting Started

All the code in this section lives in the `adbcj-demo` directory.

### Add the dependencies

For MySQL:

```xml
<dependency>
    <groupId>org.adbcj</groupId>
    <artifactId>adbcj-api</artifactId>
    <version>0.9</version>
</dependency>
<dependency>
    <groupId>org.adbcj</groupId>
    <artifactId>mysql-async-driver</artifactId>
    <version>0.9</version>
</dependency>
```

Or for H2, replace the driver artifact with `h2-async-driver`.

### Connect to a database

Create a `ConnectionManager` first. It owns the connections and shared resources such as thread pools — usually one per application. Connection URLs look like JDBC URLs with an `adbcj:` scheme:

```
adbcj:mysql://localhost/the-database
adbcj:h2://localhost/the-database
```

```java
final ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(
        "adbcj:h2://localhost/the-database",
        "user",
        "password"
);
```

### Go async

ADBCJ is built for asynchronous operations — if you want synchronous access, just use JDBC. Every operation returns a Java 8 `CompletableFuture`:

```java
connectionManager.connect().thenCompose(connection -> {
    System.out.println("Connected!");
    return connection.close();
}).thenCompose(closeComplete -> {
    return connectionManager.close();
}).whenComplete((complete, error) -> {
    if (error != null) {
        error.printStackTrace();
    }
});
```

If `CompletableFuture` doesn't fit your needs, every operation also accepts a low-level `DbCallback`, invoked with the value on success or the exception on failure:

```java
connectionManager.connect((connection, failure) -> {
    if (failure == null) {
        System.out.println("Connected!");
        connection.close((closed, closeFailure) -> { /* ... */ });
    } else {
        failure.printStackTrace();
    }
});
```

### The first SQL statements

ADBCJ sends every query to the database immediately when possible, avoiding round-trip waits. If your statements don't depend on each other, fire them all off and then wait for the combined result:

```java
connectionManager.connect().thenAccept(connection -> {
    CompletableFuture<Result> create = connection.executeUpdate(
            "CREATE TABLE IF NOT EXISTS posts(" +
            "  id int NOT NULL AUTO_INCREMENT," +
            "  title varchar(255) NOT NULL," +
            "  content TEXT NOT NULL," +
            "  PRIMARY KEY (id))");
    // Independent statements are pipelined — no waiting between them
    CompletableFuture<Result> firstInsert =
            connection.executeUpdate("INSERT INTO posts(title,content) VALUES('The Title','TheContent')");
    CompletableFuture<Result> secondInsert =
            connection.executeUpdate("INSERT INTO posts(title,content) VALUES('Another Title','More Content')");

    CompletableFuture.allOf(create, firstInsert, secondInsert)
            .thenCompose(done -> connection.executeQuery("SELECT * FROM posts"))
            .thenAccept(queryResult -> {
                // Result sets are regular Java collections, indexed from 0
                for (Row row : queryResult) {
                    System.out.println("ID: " + row.get("ID").getLong()
                            + " title: " + row.get("title").getString());
                }
            }).whenComplete((res, failure) -> {
                if (failure != null) failure.printStackTrace();
                connection.close();
                connectionManager.close();
            });
});
```

## Dealing with Failures

Because requests are handled on the driver's event loop, a raw failure stack trace contains only driver internals — nothing points back to the line in *your* code that issued the request.

ADBCJ can capture the entry point of every operation. Start the JVM with:

```
-Dorg.adbcj.debug=true
```

or enable it per connection by putting `"org.adbcj.debug"="true"` in the connection properties map. The error's `Caused by:` chain then includes the original call site (e.g. `TutorialDealingWithErrors.oupsError(...)`). This is a development/debug feature with high overhead — don't leave it on in production.

## Simple Connection Pool

Enable the built-in pool with the `org.adbcj.connectionpool.enable` property. With the pool on, closing a connection returns it to the pool, so the next `connect()` skips the connection handshake:

```java
Map<String, String> settings = new HashMap<>();
settings.put(StandardProperties.CONNECTION_POOL_ENABLE, "true");
final ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(
        "adbcj:h2://localhost:14242/mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE",
        "adbcj",
        "password1234",
        settings
);
```

Current limitations:

- No timeouts or maximum-connection limits yet.
- Prepared statements left open when a connection returns to the pool are not closed and will leak on the database side.

## Request Queue Length

ADBCJ makes it easy to fire hundreds of queries without waiting — easy enough to overload the database by accident. As protection, each connection has a pending-request queue limit, **64 by default**. Submitting a request while the queue is full fails immediately with:

```
org.adbcj.DbException: To many pending requests. The current maximum is 64. ...
```

Raise the limit via `StandardProperties.MAX_QUEUE_LENGTH`:

```java
Map<String, String> props = new HashMap<>();
props.put(StandardProperties.MAX_QUEUE_LENGTH, "200");
final ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(
        "adbcj:h2://localhost:14242/mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE",
        "adbcj", "password1234", props);
```

## Building from Source

```bash
mvn compile            # build all modules
mvn test               # run the TCK (needs running databases, see below)
```

The MySQL TCK tests expect a local MySQL instance; `mysql-docker-run.sh` starts a pre-configured Docker container. The demos and tutorials live in `adbcj-demo/` (`TutorialFirstConnection*`, `TutorialFirstSql`, `TutorialDealingWithErrors`, `TutorialConnectionPool`, `TutorialQueueLimit`).

## License

Apache License 2.0. See `LICENSE.txt`.

---

# 中文文档

[English](#asynchronous-database-connectivity-in-java-adbcj) | **中文**

ADBCJ（Asynchronous Database Connectivity in Java）让你以完全**异步、非阻塞**的方式访问关系型数据库。API 设计借鉴了 JDBC，但所有调用都是异步的：没有任何线程会因为等待数据库响应而被阻塞。

ADBCJ 的核心思想是**在单条连接上多路复用多个请求者的请求**：来自不同调用方的独立语句被**流水线化（pipeline）**——无需等待上一次网络往返就连续写入连接，响应到达后再逐一回调给各自的调用方。一条连接可以同时挂着几十个在途请求（每连接默认 64 的请求队列正是这个复用窗口）。视应用场景不同，这可以带来显著的性能提升。

ADBCJ 定位为**底层基础库**，用纯 Java 编写，方便 Scala、Kotlin、Groovy 等 JVM 语言以及更高层的库在其之上构建。

> **关键信息（Key Message）**：这个项目最大、也最持久的贡献，是用可运行的代码*证明了数据库链路层是可以多路复用的*——多个逻辑请求者共享一条物理连接，请求在线路上流水线化发送，响应再各自路由回调用方。而上层用什么编程模型——回调、`CompletableFuture`、Reactive Streams 还是虚拟线程——是一个**正交**的选择。传输层的多路复用不与其中任何一个竞争，而是可以与所有这些模型组合。

## 特性

- 提供与 JDBC 对应的异步非阻塞操作：`executeQuery`、`executeUpdate`、`prepareQuery`、`prepareUpdate`，以及事务（`beginTransaction` / `commit` / `rollback`）
- 每个操作都支持两种编程风格：
  - **Java 8 `CompletableFuture`** —— 用 `thenCompose`、`allOf` 等自由组合异步流程
  - **底层 `DbCallback` 回调** —— 方便接入你自己的 future / 异步框架
- 基于 Netty 的 **MySQL** 和 **H2 原生异步驱动**（底层不依赖 JDBC）
- **JDBC 桥接驱动**，可把任意阻塞式 JDBC 驱动包装成 ADBCJ 异步 API
- 请求**流水线**：相互独立的语句立即发出，不等待前一个往返
- 内置**连接池**（可选，默认关闭）
- **过载保护**：每个连接有待处理请求队列上限（默认 64）
- **异步堆栈追踪调试**：可选地在每个入口捕获调用方堆栈，让报错能定位到你自己的代码
- 附带 **TCK** 兼容性测试套件，所有驱动都必须通过

## 核心设计：单连接请求多路复用

在 JDBC 中，连接是一个*串行*资源：一条语句独占连接直到整个网络往返结束，所以 N 个并发语句就需要连接池里的 N 条连接，而且每条语句都要付出完整的网络延迟。ADBCJ 把这个模型反过来：每条连接维护一个在途请求队列，新语句立即写入 socket，由驱动的 Netty 事件循环按协议顺序把响应逐一匹配回挂起的回调。

以 MySQL 驱动（经典协议）为例，线路上的工作方式如下：

```
  requesters                          ADBCJ MySqlConnection                    MySQL server
  (任意线程 / future 链)                                                       (经典协议)

  +-------------+   Q1
  | requester A |-------+
  +-------------+       |        FIFO requestQueue (在途窗口 <= 64)
  +-------------+   Q2  |        +------+------+------+ - - +
  | requester B |-------+------> |  Q1  |  Q2  |  Q3  |     |  回调按发送顺序
  +-------------+       |        +------+------+------+ - - +  挂在队列里等待
  +-------------+   Q3  |           |
  | requester C |-------+           |  立即写入 socket，
  +-------------+                   v  不等待之前的网络往返

                       ============ 单条 TCP 连接 ============
       --> [COM_QUERY Q1][COM_QUERY Q2][COM_QUERY Q3] ------------->  服务器严格按
               (流水线发送: 3 条语句约 1 次往返)                        到达顺序执行
       <-- [Result   Q1 ][Result   Q2 ][Result   Q3 ] <-------------

                    |
                    v
   Netty 事件循环按序解码每个响应, 弹出队头 (requestQueue.poll()),
   完成对应调用方的回调:

       Result Q1 -> requester A    Result Q2 -> requester B    Result Q3 -> requester C
```

注意这里的配对技巧：MySQL 经典协议**没有请求 ID**——服务器只是按收到命令的顺序依次应答。正因如此，一个 FIFO 队列就足以把每个响应路由回对应的请求者；也正因如此，同一连接上的响应永远不会乱序超车（真正的乱序多路复用需要带流 ID 的协议，比如 HTTP/2 或 MySQL X Protocol）。

实际效果是：

- **网络往返开销被摊薄**：K 条独立语句的总耗时约等于一次往返，而不是 K 次。
- **连接数大幅减少**：多个逻辑请求者共享一条物理连接，而不是"一语句一连接"的池化模式。
- **背压是显式的**：在途窗口有上限（`adbcj.maxQueueLength`，默认 64），超出立即快速失败，而不是悄悄压垮数据库。

这与后来 PostgreSQL 的 pipeline 模式（libpq 14+）是同一种设计——也是连接池给不了你的、ADBCJ 最核心的东西。

## 与 R2DBC、Project Loom 的对比

R2DBC 和 Project Loom 解决的是同一个原始痛点——线程阻塞等待数据库——但切入角度不同：

| | ADBCJ | R2DBC | JDBC + Project Loom（虚拟线程） |
|---|---|---|---|
| 编程模型 | `CompletableFuture` / 原生回调 | Reactive Streams（`Publisher`，通常配 Reactor） | 在虚拟线程上写普通阻塞代码 |
| 并发单位 | 共享连接上的在途请求 | 每条语句一个 Subscription | 每条语句一个虚拟线程 + 一条池化连接 |
| 单连接流水线 | **有——这就是核心特性** | 取决于具体驱动实现，不属于规范契约 | 没有——JDBC 语义下每条连接仍是串行的 |
| 背压 | 有界请求队列，快速失败 | 基于 demand，可精确到行级流式消费 | 隐式（线程阻塞）；上限就是连接池大小 |
| 结果集 | 物化集合（另有行事件 `ResultHandler`） | 按需流式逐行下发 | JDBC `ResultSet` 游标 |
| 生态 | 极小，已停滞；MySQL + H2 原生驱动 | 活跃规范（1.0，2022），Spring Data 加持，驱动众多 | JDK 21 起的 Java 主流路线 |
| 依赖足迹 | Netty + SLF4J | 必须引入 Reactive Streams 实现 | 除 JDBC 外无 |

直白地说：**Loom 消除的是等待的"线程"成本，但消除不了"连接与往返"成本**——阻塞在 JDBC 上的虚拟线程依然独占连接，每条语句一次完整往返。**R2DBC 标准化的是响应式编程模型**，而单连接流水线只是部分驱动的实现细节，不是规范保证。ADBCJ 至今仍独特的价值，恰恰是把单连接多路复用作为一等公民显式提供——编程模型也比 Reactive Streams 简单得多，代价是生态小得多。

需要强调的是，这不是三个对手之间的竞争。Loom 和 R2DBC 回答的是*"并发的数据库代码怎么写"*——编程模型层的问题；ADBCJ 的多路复用回答的是*"语句在线路上怎么传输"*——传输层的问题。两层互相独立、并不冲突，理想的技术栈应当把它们组合起来。

## 未来演进观望

对这一细分领域走向的坦率判断：

- **Oracle 官方的异步 JDBC（ADBA）已于 2019 年放弃，理由正是押注 Loom。** Java 主流正在收敛到"阻塞 JDBC + 虚拟线程"；对大多数有热连接池的 CRUD 场景，这已经够用了。
- **R2DBC 凭借 Spring 的加持占据了响应式这块阵地**，在 API 广度上与它竞争对这个项目来说不现实。
- **流水线是上面两者都没给你的东西。** 数据库本身也在朝这个方向走（PostgreSQL pipeline 模式、MySQL X Protocol 多路复用）。所以 ADBCJ 这类设计最有意思的演进路径*不是*"异步 API"，而是"流水线化的传输层"：比如给虚拟线程提供一个看起来阻塞的 API，底层透明地把多个虚拟线程的语句复用到少量物理连接上——用 Loom 的编程模型搭配 ADBCJ 的线路效率。
- **项目现状**：上游开发已停滞（最后一个版本是 0.9）。建议把这份代码当作异步线路协议（MySQL、H2）和多路复用设计的可运行参考实现——适合学习与实验，而非生产依赖。

## 模块结构

| 模块 | Maven 坐标 | 说明 |
|---|---|---|
| `api` | `org.adbcj:adbcj-api` | 与驱动无关的 API 及支撑类 |
| `mysql` | `org.adbcj:mysql-async-driver` | 原生异步 MySQL 驱动（基于 Netty） |
| `h2` | `org.adbcj:h2-async-driver` | 原生异步 H2 驱动（基于 Netty） |
| `jdbc` | `org.adbcj:adbcj-jdbc` | 把任意 JDBC 驱动桥接为 ADBCJ API |
| `tck` | – | 驱动兼容性测试套件 |
| `adbcj-demo` | – | 可运行的教程示例（见下文） |

## 快速上手

本节所有代码都在 `adbcj-demo` 目录中。

### 添加依赖

以 MySQL 为例：

```xml
<dependency>
    <groupId>org.adbcj</groupId>
    <artifactId>adbcj-api</artifactId>
    <version>0.9</version>
</dependency>
<dependency>
    <groupId>org.adbcj</groupId>
    <artifactId>mysql-async-driver</artifactId>
    <version>0.9</version>
</dependency>
```

使用 H2 时把驱动换成 `h2-async-driver` 即可。

### 连接数据库

先创建 `ConnectionManager`。它持有所有连接及线程池等共享资源，通常一个应用只需要一个。连接 URL 格式与 JDBC 类似，前缀为 `adbcj:`：

```
adbcj:mysql://localhost/the-database
adbcj:h2://localhost/the-database
```

```java
final ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(
        "adbcj:h2://localhost/the-database",
        "user",
        "password"
);
```

### 拥抱异步

ADBCJ 为异步而生——如果你想要同步访问，直接用 JDBC 就好。每个操作都返回 Java 8 `CompletableFuture`：

```java
connectionManager.connect().thenCompose(connection -> {
    System.out.println("已连接!");
    return connection.close();
}).thenCompose(closeComplete -> {
    return connectionManager.close();
}).whenComplete((complete, error) -> {
    if (error != null) {
        error.printStackTrace();
    }
});
```

如果 `CompletableFuture` 不合需求，每个操作也接受底层 `DbCallback` 回调：成功时收到结果值，失败时收到异常：

```java
connectionManager.connect((connection, failure) -> {
    if (failure == null) {
        System.out.println("已连接!");
        connection.close((closed, closeFailure) -> { /* ... */ });
    } else {
        failure.printStackTrace();
    }
});
```

### 第一条 SQL

ADBCJ 会尽可能把查询立刻发送到数据库，避免等待网络往返。语句之间没有依赖时，直接全部发出，再统一等待结果：

```java
connectionManager.connect().thenAccept(connection -> {
    CompletableFuture<Result> create = connection.executeUpdate(
            "CREATE TABLE IF NOT EXISTS posts(" +
            "  id int NOT NULL AUTO_INCREMENT," +
            "  title varchar(255) NOT NULL," +
            "  content TEXT NOT NULL," +
            "  PRIMARY KEY (id))");
    // 独立语句会被流水线化发送，互不等待
    CompletableFuture<Result> firstInsert =
            connection.executeUpdate("INSERT INTO posts(title,content) VALUES('The Title','TheContent')");
    CompletableFuture<Result> secondInsert =
            connection.executeUpdate("INSERT INTO posts(title,content) VALUES('Another Title','More Content')");

    CompletableFuture.allOf(create, firstInsert, secondInsert)
            .thenCompose(done -> connection.executeQuery("SELECT * FROM posts"))
            .thenAccept(queryResult -> {
                // 结果集就是普通 Java 集合，下标从 0 开始
                for (Row row : queryResult) {
                    System.out.println("ID: " + row.get("ID").getLong()
                            + " 标题: " + row.get("title").getString());
                }
            }).whenComplete((res, failure) -> {
                if (failure != null) failure.printStackTrace();
                connection.close();
                connectionManager.close();
            });
});
```

## 错误排查

由于请求在驱动的事件循环（event loop）线程上处理，原始异常堆栈里只有驱动内部调用，完全看不到是*你的*哪行代码发起了请求。

ADBCJ 可以捕获每个操作的入口堆栈。在 JVM 启动参数中加入：

```
-Dorg.adbcj.debug=true
```

或在连接属性 map 中加入 `"org.adbcj.debug"="true"` 按连接开启。开启后异常的 `Caused by:` 链中会包含原始调用位置（例如 `TutorialDealingWithErrors.oupsError(...)`）。这是开发调试功能，开销很大，不要在生产环境常开。

## 简易连接池

通过 `org.adbcj.connectionpool.enable` 属性启用内置连接池。启用后，关闭连接会把它归还到池中，下次 `connect()` 可以跳过连接握手：

```java
Map<String, String> settings = new HashMap<>();
settings.put(StandardProperties.CONNECTION_POOL_ENABLE, "true");
final ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(
        "adbcj:h2://localhost:14242/mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE",
        "adbcj",
        "password1234",
        settings
);
```

当前实现的局限：

- 尚不支持超时、最大连接数等限制。
- 连接归还连接池时，未关闭的预编译语句不会被关闭，会在数据库端泄漏。

## 请求队列长度

用 ADBCJ 很容易在不等待结果的情况下发出成百上千条查询——也就很容易在不知不觉中压垮数据库。作为保护，每个连接默认最多允许 **64** 个待处理请求；队列满时再提交请求会立即失败：

```
org.adbcj.DbException: To many pending requests. The current maximum is 64. ...
```

可以通过 `StandardProperties.MAX_QUEUE_LENGTH` 调大上限：

```java
Map<String, String> props = new HashMap<>();
props.put(StandardProperties.MAX_QUEUE_LENGTH, "200");
final ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(
        "adbcj:h2://localhost:14242/mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE",
        "adbcj", "password1234", props);
```

## 源码构建

```bash
mvn compile            # 编译全部模块
mvn test               # 运行 TCK 测试（需要先启动数据库，见下）
```

MySQL 的 TCK 测试需要本地 MySQL 实例，可用 `mysql-docker-run.sh` 启动预配置的 Docker 容器。教程示例位于 `adbcj-demo/` 目录（`TutorialFirstConnection*`、`TutorialFirstSql`、`TutorialDealingWithErrors`、`TutorialConnectionPool`、`TutorialQueueLimit`）。

## 许可证

Apache License 2.0，详见 `LICENSE.txt`。
