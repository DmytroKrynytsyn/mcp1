mcp1
Multi-module Kotlin project that implements a simple Model Context Protocol (MCP) server and a client.

This repository contains two main modules:

kotlin_server — a Ktor-based MCP server implementation exposing prompts, tools and resources (examples: weather prompt/tool, web search resource).
kotlin_client — a CLI client that can connect to the MCP server and run an interactive chat loop. The client includes a Gradle wrapper for easy building and running.
Key source locations

Server sources: kotlin_server/app/src/main/kotlin/org/dkedu
Client sources: kotlin_client/app/src/main/kotlin/org/dkedu
Quick description

The server (main class: org.dkedu.AppServerKt) uses Ktor and the MCP Kotlin SDK to expose SSE endpoints and register example prompts, tools and resources.
The client (main class: org.dkedu.AppClientKt) provides an interactive chat loop and will attempt to connect to an MCP server; if not available it will run in a basic chat mode.
Prerequisites

Java 21 (project is configured to use Java toolchain 21)
Gradle (or use the Gradle wrapper available for the client module)
Build & run (client)

Open a terminal and go to the client module:

cd kotlin_client

Use the bundled Gradle wrapper to run the client:

./gradlew :app:run

Alternatively build a fat JAR (shadow) and run it:

./gradlew :app:shadowJar

then run the produced JAR (check the actual filename under kotlin_client/app/build/libs)
java -jar kotlin_client/app/build/libs/.jar

Build & run (server)

The kotlin_server module does not include a Gradle wrapper in the repo root; you can either open the module in an IDE (IntelliJ IDEA) or use a locally installed Gradle.
From the module root (requires Gradle installed):

cd kotlin_server
gradle :app:run
Or build a shadow JAR and run it:

gradle :app:shadowJar
java -jar kotlin_server/app/build/libs/<server-shadow-jar>.jar
Notes & tips

If you plan to run client against the local server, first start the server on a port (default 8080). Then run the client and it will attempt to connect.
If you use the client wrapper ./gradlew in kotlin_client, it will download the Gradle wrapper automatically and run with it.
If you prefer IDE workflows, open the project/module in IntelliJ and run org.dkedu.AppServerKt or org.dkedu.AppClientKt from the Gradle run configurations or directly as Kotlin applications.
Where to look next

Server: kotlin_server/app/src/main/kotlin/org/dkedu/AppServer.kt — server configuration and handlers
Client: kotlin_client/app/src/main/kotlin/org/dkedu/AppClient.kt — client entrypoint and chat loop
If you'd like, I can also:

run a quick build for both modules and report back any build/test failures, or
expand the README with examples (how the chat looks, environment variables, expected logs).
