package com.ds.project;

import java.io.IOException;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ds.messaging.server.MessagingServer;
import com.ds.messaging.ui.DashboardServer;
import com.ds.messaging.utils.Config;

/**
 * Main entry point for the Distributed Systems Project
 * SLIIT - Year 2, Semester 2
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("=== Distributed Systems Project Started ===");
        String configPath = args.length > 0 ? args[0] : "src/main/resources/application.properties";
        MessagingServer server = null;
        DashboardServer dashboard = null;
        
        try {
            Config config = new Config();
            try {
                config.loadFromFile(configPath);
                logger.info("Loaded configuration from {}", configPath);
            } catch (IOException e) {
                logger.warn("Could not load config at {}. Using defaults.", configPath);
            }

            server = new MessagingServer(config);
            server.start();

            if (config.isUiEnabled()) {
                dashboard = new DashboardServer(server, config.getUiPort());
                dashboard.start();
                logger.info("Dashboard available at http://localhost:{}", dashboard.getBoundPort());
            }

            logger.info("Messaging cluster started with {} nodes", server.getNodeCount());
            runConsole(server);
            
            if (dashboard != null) {
                dashboard.stop();
            }
            server.stop();
            logger.info("Project shutdown complete");
            
        } catch (RuntimeException e) {
            logger.error("Failed to initialize project", e);
            if (dashboard != null) {
                dashboard.stop();
            }
            if (server != null && server.isRunning()) {
                server.stop();
            }
            System.exit(1);
        }
    }

    private static void runConsole(MessagingServer server) {
        printHelp();
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("cluster> ");
                if (!scanner.hasNextLine()) {
                    break;
                }

                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\s+");
                String cmd = parts[0].toLowerCase();

                if ("exit".equals(cmd) || "quit".equals(cmd)) {
                    break;
                }

                try {
                    switch (cmd) {
                        case "help":
                            printHelp();
                            break;
                        case "status":
                            System.out.println(server.describeNodeStatuses());
                            break;
                        case "leader":
                            if (server.getLeader() == null) {
                                System.out.println("No leader currently elected.");
                            } else {
                                System.out.println("Leader: " + server.getLeader().getNodeId());
                            }
                            break;
                        case "kill":
                            requireArgs(parts, 2, "kill <nodeId>");
                            System.out.println(server.killNode(parts[1])
                                    ? "Killed node " + parts[1]
                                    : "Node not found: " + parts[1]);
                            break;
                        case "recover":
                            if (parts.length == 2) {
                                System.out.println(server.recoverNode(parts[1])
                                        ? "Recovered node " + parts[1]
                                        : "Could not recover node " + parts[1]);
                            } else {
                                requireArgs(parts, 4, "recover <nodeId> <host> <port>");
                                int port = Integer.parseInt(parts[3]);
                                System.out.println(server.recoverNode(parts[1], parts[2], port)
                                        ? "Recovered node " + parts[1] + " on " + parts[2] + ":" + port
                                        : "Could not recover node " + parts[1]);
                            }
                            break;
                        case "addnode":
                            requireArgs(parts, 4, "addnode <nodeId> <host> <port>");
                            server.addNode(parts[1], parts[2], Integer.parseInt(parts[3]));
                            System.out.println("Added node " + parts[1]);
                            break;
                        case "setport":
                            requireArgs(parts, 3, "setport <nodeId> <newPort>");
                            int newPort = Integer.parseInt(parts[2]);
                            System.out.println(server.updateNodePort(parts[1], newPort)
                                    ? "Updated " + parts[1] + " to port " + newPort
                                    : "Could not update port for " + parts[1]);
                            break;
                        case "setnodes":
                            requireArgs(parts, 2, "setnodes <count>");
                            int count = Integer.parseInt(parts[1]);
                            server.resizeCluster(count);
                            System.out.println("Cluster resized to " + count + " nodes.");
                            break;
                        case "send":
                            requireArgs(parts, 4, "send <fromNodeId> <toNodeId> <message>");
                            String message = line.substring(line.indexOf(parts[3]));
                            System.out.println(server.sendMessage(parts[1], parts[2], message)
                                    ? "Message sent."
                                    : "Failed to send message.");
                            break;
                        default:
                            System.out.println("Unknown command. Type 'help' for available commands.");
                            break;
                    }
                } catch (RuntimeException ex) {
                    System.out.println("Command failed: " + ex.getMessage());
                }
            }
        }
    }

    private static void requireArgs(String[] parts, int minLength, String usage) {
        if (parts.length < minLength) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  help                                 Show command list");
        System.out.println("  status                               Show node status summary");
        System.out.println("  leader                               Show current leader");
        System.out.println("  send <from> <to> <message>          Send a message between nodes");
        System.out.println("  kill <nodeId>                        Kill a node");
        System.out.println("  recover <nodeId>                     Recover a killed node");
        System.out.println("  recover <nodeId> <host> <port>       Recover/add node at endpoint");
        System.out.println("  addnode <nodeId> <host> <port>       Add new node");
        System.out.println("  setport <nodeId> <newPort>           Change node port");
        System.out.println("  setnodes <count>                     Resize cluster (node1..nodeN)");
        System.out.println("  (UI) Open http://localhost:8080      Web dashboard (if enabled)");
        System.out.println("  exit                                 Stop cluster and quit");
    }
}
