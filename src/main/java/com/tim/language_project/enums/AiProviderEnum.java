package com.tim.language_project.enums;

import lombok.Getter;

/**
 * 本專案會用到的外部 AI 服務商。
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
