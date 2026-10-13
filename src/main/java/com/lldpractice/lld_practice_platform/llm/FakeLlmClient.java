package com.lldpractice.lld_practice_platform.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** A scripted LlmClient for tests and demos: canned JSON, timeouts, transport errors, or malformed output. */
public class FakeLlmClient implements LlmClient {
    @FunctionalInterface
    public interface Step {
        LlmResponse run(LlmRequest request) throws LlmException;
    }

    private final List<Step> steps = new ArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final List<LlmRequest> requests = new CopyOnWriteArrayList<>();

    /** Clears the script and counters so one instance can be reused between tests. */
    public FakeLlmClient reset() {
        steps.clear();
        calls.set(0);
        requests.clear();
        return this;
    }

    public FakeLlmClient then(Step step) {
        steps.add(step);
        return this;
    }

    public FakeLlmClient thenReturn(String text) {
        return then(r -> new LlmResponse(text));
    }

    public FakeLlmClient thenTimeOut() {
        return then(r -> {
            throw new LlmException.Timeout("simulated timeout");
        });
    }

    public FakeLlmClient thenFailTransport() {
        return then(r -> {
            throw new LlmException.Transport("simulated transport error", null);
        });
    }

    public FakeLlmClient thenSleep(long millis, String text) {
        return then(r -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException.Timeout("interrupted");
            }
            return new LlmResponse(text);
        });
    }

    /** The last scripted step repeats once the script runs out. */
    @Override
    public LlmResponse complete(LlmRequest request) throws LlmException {
        requests.add(request);
        int i = calls.getAndIncrement();
        if (steps.isEmpty()) throw new LlmException("FakeLlmClient has no script");
        return steps.get(Math.min(i, steps.size() - 1)).run(request);
    }

    public int callCount() {
        return calls.get();
    }

    public List<LlmRequest> requests() {
        return List.copyOf(requests);
    }
}
