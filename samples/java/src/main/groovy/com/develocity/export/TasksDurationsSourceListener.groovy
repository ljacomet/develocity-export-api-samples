package com.develocity.export

import groovy.json.JsonSlurper
import okhttp3.sse.EventSource
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import java.time.Duration
import java.time.Instant

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
class TasksDurationsSourceListener extends ExportApiJavaExample.PrintFailuresEventSourceListener {
    private Map<Long, Object> taskStartedEvents = [:];

    @Override
    void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
        if (type.equals("BuildEvent")) {
            def json = new JsonSlurper().parse(data)
            if (json.type.eventType == "TaskStarted") {
                    taskStartedEvents[json.data.id as Long] = json

            } else if (json.type.eventType == "TaskFinished") {
                def started = taskStartedEvents[json.data.id as Long]
                String task = json.data.path
                Duration duration = Duration.between(Instant.ofEpochMilli(json.timestamp as long), Instant.ofEpochMilli(started.timestamp as long))
                println "$task -> $duration"
            }
        }
    }
}
