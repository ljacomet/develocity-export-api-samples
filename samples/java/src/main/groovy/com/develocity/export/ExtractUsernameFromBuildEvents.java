package com.develocity.export;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

class ExtractUsernameFromBuildEvents extends ExportApiJavaExample.PrintFailuresEventSourceListener {
    private final ExportApiJavaExample.BuildTool buildTool;
    private final String buildId;
    private final CompletableFuture<String> username = new CompletableFuture<>();

    public ExtractUsernameFromBuildEvents(ExportApiJavaExample.BuildTool buildTool, String buildId) {
        this.buildTool = buildTool;
        this.buildId = buildId;
    }

    public CompletableFuture<String> getUsername() {
        return username;
    }

    @Override
    public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
        System.out.println("Streaming events for : " + buildId);
    }

    @Override
    public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
        if (type.equals("BuildEvent")) {
            JsonNode eventJson = ExportApiJavaExample.parse(data);
            username.complete(buildTool.getExtractUsername().apply(eventJson));
        }
    }

    @Override
    public void onClosed(@NotNull EventSource eventSource) {
        // Complete only sets the value if it hasn't already been set
        username.complete("<unknown>");
    }
}
