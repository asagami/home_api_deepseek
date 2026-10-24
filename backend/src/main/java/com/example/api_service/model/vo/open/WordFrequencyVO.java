package com.example.api_service.model.vo.open;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WordFrequencyVO", description = "词频统计项")
public record WordFrequencyVO(
        @Schema(description = "单词", example = "hello") String word,
        @Schema(description = "出现次数", example = "2") long count

) {
}
