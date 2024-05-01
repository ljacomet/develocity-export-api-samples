package com.develocity.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.time.Instant.now;

public final class ExportApiJavaExample {

    private static final HttpUrl DEVELOCITY_SERVER_URL = HttpUrl.parse("https://ge.gradle.org");
    private static final String EXPORT_API_USERNAME = System.getenv("EXPORT_API_USER");
    private static final String EXPORT_API_PASSWORD = System.getenv("EXPORT_API_PASSWORD");
    private static final String EXPORT_API_ACCESS_KEY = System.getenv("EXPORT_API_ACCESS_KEY");
    // must be at least 2
    private static final int MAX_BUILD_SCANS_STREAMED_CONCURRENTLY = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum BuildTool {
        GRADLE("ProjectStructure,TaskStarted,TaskFinished", json -> json.get("data").get("username").asText()),
        ;

        private final String buildAgentEvent;
        private final Function<JsonNode, String> extractUsername;

        BuildTool(String buildAgentEvent, Function<JsonNode, String> extractUsername) {
            this.buildAgentEvent = buildAgentEvent;
            this.extractUsername = extractUsername;
        }

        public String getBuildAgentEvent() {
            return buildAgentEvent;
        }

        public Function<JsonNode, String> getExtractUsername() {
            return extractUsername;
        }
    }

    public static void main(String[] args) throws Exception {
        Instant since1Day = now().minus(Duration.ofHours(12));

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ZERO)
                .readTimeout(Duration.ZERO)
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(MAX_BUILD_SCANS_STREAMED_CONCURRENTLY, 30, TimeUnit.SECONDS))
                .authenticator(Authenticators.bearerTokenOrBasic(EXPORT_API_ACCESS_KEY, EXPORT_API_USERNAME, EXPORT_API_PASSWORD))
                .protocols(ImmutableList.of(Protocol.HTTP_1_1))
                .build();
        httpClient.dispatcher().setMaxRequests(MAX_BUILD_SCANS_STREAMED_CONCURRENTLY);
        httpClient.dispatcher().setMaxRequestsPerHost(MAX_BUILD_SCANS_STREAMED_CONCURRENTLY);

        EventSource.Factory eventSourceFactory = EventSources.createFactory(httpClient);
        ExtractTaskDurationsFromBuilds listener = new ExtractTaskDurationsFromBuilds(eventSourceFactory);

        eventSourceFactory.newEventSource(requestBuilds(since1Day), listener);
        Map<String, AverageDuration> averageDurations = new HashMap<>();
        List<Map<String, Duration>> taskDurations = listener.getTaskDurations().get();
        taskDurations.stream()
                        .flatMap( m -> m.entrySet().stream())
                                .forEach(taskDuration -> averageDurations
                                        .computeIfAbsent(taskDuration.getKey(), __ -> new AverageDuration())
                                        .add(taskDuration.getValue()));
        writeResultsToFile(averageDurations);

        System.out.println("Results: " + averageDurations.size());

        // Cleanly shuts down the HTTP client, which speeds up process termination
        shutdown(httpClient);
    }

    private static void writeResultsToFile(Map<String, AverageDuration> averageDurations) {
        Properties properties = new Properties();
        averageDurations.entrySet().stream().forEach(e -> properties.put(e.getKey(), String.valueOf(e.getValue().getAvg().toMillis())));
        try (FileWriter fw = new FileWriter(new File(System.getProperty("user.home"), ".gradle/buildDurations.properties"))) {
            properties.store(fw, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private static Request requestBuilds(Instant since1Day) {
        return new Request.Builder()
                .url(DEVELOCITY_SERVER_URL.resolve("/build-export/v2/builds/since/" + since1Day.toEpochMilli()))
                .build();
    }

    @NotNull
    private static Request requestBuildEvents(BuildTool buildTool, String buildId) {
        return new Request.Builder()
                .url(DEVELOCITY_SERVER_URL.resolve("/build-export/v2/build/" + buildId + "/events?eventTypes=" + buildTool.getBuildAgentEvent()))
                .build();
    }

    private static class ExtractTaskDurationsFromBuilds extends PrintFailuresEventSourceListener {
        private final List<CompletableFuture<Map<String, Duration>>> taskDurationFutures = new ArrayList<>();
        private final CompletableFuture<List<Map<String, Duration>>> taskDurations = new CompletableFuture<>();
        private final EventSource.Factory eventSourceFactory;
        private ExtractTaskDurationsFromBuilds(EventSource.Factory eventSourceFactory) {
            this.eventSourceFactory = eventSourceFactory;
        }

        public CompletableFuture<List<Map<String, Duration>>> getTaskDurations() {
            return taskDurations;
        }

        @Override
        public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
            System.out.println("Streaming builds...");
        }

        @Override
        public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
            JsonNode json = parse(data);
            JsonNode buildToolJson = json.get("toolType");
            if (buildToolJson == null || buildToolJson.asText().equals("gradle")) {
                final String buildId = json.get("buildId").asText();
                final BuildTool buildTool = BuildTool.valueOf(buildToolJson != null ? buildToolJson.asText().toUpperCase() : "GRADLE");

                System.out.println("buildId = " + buildId);
                Request request = requestBuildEvents(buildTool, buildId);

                TasksDurationsSourceListener listener = new TasksDurationsSourceListener();
                eventSourceFactory.newEventSource(request, listener);
                taskDurationFutures.add(listener.getDurations());
            }
        }

        @Override
        public void onClosed(@NotNull EventSource eventSource) {
            System.out.println("ExtractUsernamesFromBuilds.onClosed");
            taskDurations.complete(taskDurationFutures.stream()
                    .map(u -> {
                        try {
                            return u.get();
                        } catch (InterruptedException | ExecutionException e) {
                            e.printStackTrace();
                            return Collections.<String, Duration>emptyMap();
                        }
                    })
                    .collect(Collectors.toList())
            );
        }
    }

    public static class PrintFailuresEventSourceListener extends EventSourceListener {
        @Override
        public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
            if (t != null) {
                System.err.println("FAILED: " + t.getMessage());
                t.printStackTrace();
            }
            if (response != null) {
                System.err.println("Bad response: " + response);
                System.err.println("Response body: " + getResponseBody(response));
            }
            eventSource.cancel();
            this.onClosed(eventSource);
        }

        private String getResponseBody(Response response) {
            try {
                return response.body().string();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static JsonNode parse(String data) {
        try {
            return MAPPER.readTree(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static void shutdown(OkHttpClient httpClient) {
        httpClient.dispatcher().cancelAll();
        MoreExecutors.shutdownAndAwaitTermination(httpClient.dispatcher().executorService(), Duration.ofSeconds(10));
    }
}
