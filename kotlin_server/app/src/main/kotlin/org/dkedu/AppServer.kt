package org.dkedu

import org.slf4j.LoggerFactory
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.int
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.application.install
import io.ktor.server.sse.sse
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.plugins.statuspages.statusFile
import io.ktor.server.request.uri
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.Role
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.runBlocking

fun configureServer(): Server {
    val logger = LoggerFactory.getLogger("org.dkedu.Config")
    logger.info("Creating server configuration")
    val server = Server(
        Implementation(
            name = "weather-mcp-server",
            version = "0.1.0",
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = true),
                resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                tools = ServerCapabilities.Tools(listChanged = true),
            ),
        ),
    )

    logger.info("Adding weather prompt handler")
    server.addPrompt(
        name = "weather-prompt",
        description = "Get the weather for a given city",
    ) { request ->
        logger.info("Processing weather prompt request")
        GetPromptResult(
            "Get the weather for a given city",
            messages = listOf(
                PromptMessage(
                    role = Role.user,
                    content = TextContent(
                        "Get the weather for a given city",
                    ),
                ),
            ),
        )
    }

    logger.info("Adding weather tool handler")
    server.addTool(
        name = "weather-tool",
        description = "Get the weather for a given city",
        inputSchema = Tool.Input(),
    ) { request ->
        logger.info("Processing weather tool request for city: {}", request.arguments["City"])
        CallToolResult(
            content = listOf(TextContent("The weather in ${request.arguments["City"]} is 20 degrees Celsius")),
        )
    }

    logger.info("Adding web search resource handler")
    server.addResource(
        uri = "https://search.com/",
        name = "Web Search",
        description = "Web search engine",
        mimeType = "text/html",
    ) { request ->
        logger.info("Processing web search request for URI: {}", request.uri)
        ReadResourceResult(
            contents = listOf(
                TextResourceContents("Placeholder content for ${request.uri}", request.uri, "text/html"),
            ),
        )
    }

    return server
}

suspend fun runSseMcpServerWithPlainConfiguration(port: Int) {
    val logger = LoggerFactory.getLogger("org.dkedu.Server")
    logger.info("Starting SSE server on port {}", port)
    logger.info("Use inspector to connect to http://localhost:{}/sse", port)

    embeddedServer(CIO, host = "127.0.0.1", port = port) {
        install(StatusPages) {
            status(HttpStatusCode.NotFound) { call, _ ->
                val requestPath = call.request.uri
                logger.info("404 Not Found - Request path: {}", requestPath)
                call.respondText("404: Page Not Found - The requested path '$requestPath' does not exist", status = HttpStatusCode.NotFound)
            }
        }

        // Use the MCP Ktor plugin to register the MCP endpoints (SSE etc.) provided by the SDK
        mcp {
            logger.debug("Configuring MCP server via SDK mcp plugin")
            return@mcp configureServer()
        }
    }.start(wait = true)
}


class MCPServer : CliktCommand() {
    private val logger = LoggerFactory.getLogger(MCPServer::class.java)
    
    private val port: Int by option(
        help = "Port to listen on",
        envvar = "MCP_PORT"
    ).int().default(8080).validate {
        require(it in 1..65535) { "Port must be between 1 and 65535" }
    }

    override fun run() = runBlocking {
        logger.info("Starting MCP Server")
        logger.info("Starting server on port {}", port)
        runSseMcpServerWithPlainConfiguration(port)
    }
}

fun main(args: Array<String>) = MCPServer().main(args)
