package com.tim.language_project.enums;

import lombok.Getter;

/**
 * External AI service providers used by this application.
 */
@Getter
public enum AiProviderEnum {

    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    GOOGLE("Google Cloud"),
    AZURE("Microsoft Azure");

    private final String displayName;

    AiProviderEnum(String displayName) {
        this.displayName = displayName;
    }
}
