package org.dkedu

import org.slf4j.LoggerFactory
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.Model
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolUnion
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SSEClientTransport
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.sse.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.optionals.getOrNull

class MCPClient : AutoCloseable {
    private val logger = LoggerFactory.getLogger(MCPClient::class.java)

    // Configures using the `ANTHROPIC_API_KEY` and `ANTHROPIC_AUTH_TOKEN` environment variables
    private val anthropic = AnthropicOkHttpClient.fromEnv().also {
        logger.info("Initialized Anthropic client")
    }

    // Initialize MCP client
    private val mcp: Client = Client(clientInfo = Implementation(name = "mcp-client-cli", version = "1.0.0"))

    private val messageParamsBuilder: MessageCreateParams.Builder = MessageCreateParams.builder()
        .model(Model.CLAUDE_3_7_SONNET_20250219)
        .maxTokens(1024)

    // List of tools offered by the server
    private var tools: List<ToolUnion> = emptyList()

    private fun JsonObject.toJsonValue(): JsonValue {
        val mapper = ObjectMapper()
        val node = mapper.readTree(this.toString())
        return JsonValue.fromJsonNode(node)
    }

    // Connect to the server using HTTP SSE transport
    suspend fun connectToServer() {
        try {
            logger.info("Attempting to connect to MCP server")
            // Setup HTTP SSE transport using localhost:8080/sse
            val httpClient = HttpClient(CIO) {
                // Request logging via Ktor's Logging plugin caused runtime
                // classpath issues in some environments (missing observer
                // class). To avoid that in the fat JAR we create, we keep
                // default client and rely on server-side logs for request
                // verification. If you want request logging, re-add the
                // Logging plugin and ensure the matching Ktor observer
                // dependency is present at runtime.
                install(SSE)
            }
            logger.info("Created HTTP client with SSE support")
            
            val transport = SSEClientTransport(
                client = httpClient,
                urlString = "http://localhost:8080/sse"
            )
            logger.info("Created SSE transport for endpoint: http://localhost:8080/sse")

            // Connect the MCP client to the server using the transport
            mcp.connect(transport)

            val toolsResult = mcp.listTools()
            tools = toolsResult?.tools?.map { tool ->
                ToolUnion.ofTool(
                    Tool.builder()
                        .name(tool.name)
                        .description(tool.description ?: "")
                        .inputSchema(
                            Tool.InputSchema.builder()
                                .type(JsonValue.from(tool.inputSchema.type))
                                .properties(tool.inputSchema.properties.toJsonValue())
                                .putAdditionalProperty("required", JsonValue.from(tool.inputSchema.required))
                                .build(),
                        )
                        .build(),
                )
            } ?: emptyList()
            logger.info("Connected to server with tools: {}", tools.joinToString(", ") { it.tool().get().name() })
        } catch (e: Exception) {
            logger.error("Failed to connect to MCP server", e)
            throw e
        }
    }

    // Process a user query and return a string response
    suspend fun processQuery(query: String): String {
        // Create an initial message with a user's query
        val messages = mutableListOf(
            MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(query)
                .build(),
        )

        // Send the query to the Anthropic model and get the response
        val response = if (tools.isNotEmpty()) {
            anthropic.messages().create(
                messageParamsBuilder
                    .messages(messages)
                    .tools(tools)
                    .build(),
            )
        } else {
            anthropic.messages().create(
                messageParamsBuilder
                    .messages(messages)
                    .build(),
            )
        }

        val finalText = mutableListOf<String>()
        response.content().forEach { content ->
            when {
                // Append text outputs from the response
                content.isText() -> finalText.add(content.text().getOrNull()?.text() ?: "")

                // If the response indicates a tool use, process it further
                content.isToolUse() -> {
                    val toolName = content.toolUse().get().name()
                    val toolArgs =
                        content.toolUse().get()._input().convert(object : TypeReference<Map<String, JsonValue>>() {})

                    // Call the tool with provided arguments
                    logger.info("Calling tool: {} with arguments: {}", toolName, toolArgs)
                    val result = mcp.callTool(
                        name = toolName,
                        arguments = toolArgs ?: emptyMap(),
                    )
                    logger.info("Tool execution completed")
                    finalText.add("[Calling tool $toolName with args $toolArgs]")

                    // Add the tool result message to the conversation
                    messages.add(
                        MessageParam.builder()
                            .role(MessageParam.Role.USER)
                            .content(
                                """
                                        "type": "tool_result",
                                        "tool_name": $toolName,
                                        "result": ${result?.content?.joinToString("\n") {
                                    (it as TextContent).text ?: ""
                                }}
                                """.trimIndent(),
                            )
                            .build(),
                    )

                    // Retrieve an updated response after tool execution
                    val aiResponse = anthropic.messages().create(
                        messageParamsBuilder
                            .messages(messages)
                            .build(),
                    )

                    // Append the updated response to final text
                    finalText.add(aiResponse.content().first().text().getOrNull()?.text() ?: "")
                }
            }
        }

        return finalText.joinToString("\n", prefix = "", postfix = "")
    }

    // Main chat loop for interacting with the user
    suspend fun chatLoop() {
        logger.info("Starting MCP Client chat loop")
        println("\nMCP Client Started!")
        println("Type your queries or 'quit' to exit.")

        while (true) {
            try {
                print("\nQuery: ")
                val message = readLine() ?: break
                if (message.lowercase() == "quit") {
                    logger.info("User requested to quit")
                    break
                }
                if (message.isBlank()) continue
                
                logger.info("Processing user query: {}", message)
                val response = processQuery(message)
                println("\n$response")
            } catch (e: Exception) {
                logger.error("Error processing query", e)
                println("\nError processing query: ${e.message}")
            }
        }
        logger.info("Chat loop ended")
    }

    override fun close() {
        logger.info("Closing MCP client")
        runBlocking {
            mcp.close()
            anthropic.close()
        }
        logger.info("MCP client closed successfully")
    }
}