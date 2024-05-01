package com.develocity.export

import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import okhttp3.Response
import okhttp3.sse.EventSource
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * Started
 * id: 2754
 event: BuildEvent
 data: {"timestamp":1714284215557,"type":{"majorVersion":1,"minorVersion":5,"eventType":"TaskStarted"},
 "data":{"id":-7978837427190732235,"buildPath":":","path":":java-platform:compileJava","className":"org.gradle.api.tasks.compile.JavaCompile","thread":null,"noActions":false}}
 *
 * Finished
 * id: 5041
 event: BuildEvent
 data: {"timestamp":1714285311711,"type":{"majorVersion":1,"minorVersion":8,"eventType":"TaskFinished"},
 "data":{"id":6008449488264751900,"path":":dependency-management:forkingIntegTest","outcome":"success","skipMessage":null,"cacheable":false,"cachingDisabledReasonCategory":"DO_NOT_CACHE_IF_SPEC_SATISFIED","cachingDisabledExplanation":"Task is untracked because: All tests should re-run","originBuildInvocationId":null,"originBuildCacheKey":null,"originExecutionTime":null,"actionable":true,"upToDateMessages":["Task is untracked because: All tests should re-run"],"skipReasonMessage":null,"cachingDisabledReason":null}}
 */
@CompileStatic
class TasksDurationsSourceListener extends ExportApiJavaExample.PrintFailuresEventSourceListener {

    private Map<Long, Object> taskStartedEvents = [:]
    private Map<String, Duration> durations = [:]
    private CompletableFuture<Map<String, Duration>> result = new CompletableFuture<>()
    private String rootProjectName

    CompletableFuture<Map<String, Duration>> getDurations() {
        result
    }

    @Override
    void onClosed(@NotNull EventSource eventSource) {
        result.complete(durations.collectEntries { k, v -> [rootProjectName + k, v]})
    }

    @Override
    void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
    }

    @Override
    void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
        if (type.equals("BuildEvent")) {
            parseEvent(data)
        }
    }

    @CompileDynamic
    private void parseEvent(String data) {
        def json = new JsonSlurper().parseText(data)
        if (json.type.eventType == "TaskStarted") {
            taskStartedEvents[json.data.id as Long] = json

        } else if (json.type.eventType == "TaskFinished") {
            if (json.data.outcome == 'success') {
                def started = taskStartedEvents.remove(json.data.id as Long)
                String task = json.data.path
                Duration duration = Duration.between(Instant.ofEpochMilli(started.timestamp as long), Instant.ofEpochMilli(json.timestamp as long))
                durations[task] = duration
            }
        } else if (json.type.eventType == "ProjectStructure") {
            rootProjectName = json.data.rootProjectName
        }
    }
}
