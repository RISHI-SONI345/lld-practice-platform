package com.lldpractice.lld_practice_platform.llm;

public interface LlmClient {
    LlmResponse complete(LlmRequest request) throws LlmException;
}
