package io.litoria.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;

import jakarta.inject.Inject;

@CommandDefinition(name = "serve", description = "Serve generated slides over HTTP for RevealJS speaker view")
public class ServeCommand implements Command<CommandInvocation> {

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry(".html", "text/html; charset=utf-8"),
            Map.entry(".css", "text/css; charset=utf-8"),
            Map.entry(".js", "application/javascript; charset=utf-8"),
            Map.entry(".json", "application/json; charset=utf-8"),
            Map.entry(".png", "image/png"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".svg", "image/svg+xml"),
            Map.entry(".ico", "image/x-icon"),
            Map.entry(".mp4", "video/mp4"),
            Map.entry(".webm", "video/webm"),
            Map.entry(".woff", "font/woff"),
            Map.entry(".woff2", "font/woff2"),
            Map.entry(".ttf", "font/ttf"),
            Map.entry(".eot", "application/vnd.ms-fontobject")
    );

    @Option(shortName = 'p', name = "port",
            description = "HTTP port",
            defaultValue = "8080")
    private int port;

    @Argument(description = "Directory containing generated slides")
    private String directory;

    @Inject
    Vertx vertx;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        if (directory == null || directory.isBlank()) {
            invocation.println("Directory path is required. Example: litoria serve /path/to/generated/slides");
            return CommandResult.FAILURE;
        }

        Path dir = Path.of(directory).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            invocation.println("Error: " + dir + " is not a directory");
            return CommandResult.FAILURE;
        }

        Path indexFile = findIndexFile(dir);
        if (indexFile == null) {
            invocation.println("Error: no HTML file found in " + dir);
            return CommandResult.FAILURE;
        }

        String indexName = dir.relativize(indexFile).toString();
        CountDownLatch stopLatch = new CountDownLatch(1);

        HttpServer server = vertx.createHttpServer();
        server.requestHandler(req -> {
            String path = req.path();
            if (path.equals("/")) {
                path = "/" + indexName;
            }

            Path filePath = dir.resolve(path.substring(1)).normalize();
            if (!filePath.startsWith(dir) || !Files.isRegularFile(filePath)) {
                req.response().setStatusCode(404).end("Not found");
                return;
            }

            try {
                byte[] content = Files.readAllBytes(filePath);
                String contentType = guessContentType(filePath.toString());
                req.response()
                        .putHeader("Content-Type", contentType)
                        .putHeader("Cache-Control", "no-cache")
                        .end(Buffer.buffer(content));
            } catch (IOException e) {
                req.response().setStatusCode(500).end("Internal server error");
            }
        });

        server.listen(port).onComplete(result -> {
            if (result.succeeded()) {
                invocation.println("Serving slides from " + dir);
                invocation.println("Open http://localhost:" + port);
                invocation.println("Press 'S' in the browser for speaker view");
                invocation.println("\nPress Ctrl+C to stop");
            } else {
                invocation.println("Error: failed to start server on port " + port + " — " + result.cause().getMessage());
                stopLatch.countDown();
            }
        });

        try {
            stopLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return CommandResult.SUCCESS;
    }

    private Path findIndexFile(Path dir) {
        try (var files = Files.list(dir)) {
            return files
                    .filter(p -> p.toString().endsWith(".html"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private String guessContentType(String path) {
        int dot = path.lastIndexOf('.');
        if (dot >= 0) {
            String ext = path.substring(dot).toLowerCase();
            return CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
        }
        return "application/octet-stream";
    }
}
