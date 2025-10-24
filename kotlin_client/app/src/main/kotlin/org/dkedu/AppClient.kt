package org.dkedu

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    MCPClient().use { client ->
        try {
            // Try to connect to the MCP server
            client.connectToServer()
            
            // Start the interactive chat loop
            client.chatLoop()
        } catch (e: Exception) {
            println("Warning: Could not connect to MCP server: ${e.message}")
            println("Starting in basic chat mode (no MCP tools available)...")
            
            // Start the interactive chat loop even without MCP server
            client.chatLoop()
        }
    }
}