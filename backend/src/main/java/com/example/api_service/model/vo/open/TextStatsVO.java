package com.example.api_service.model.vo.open;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "TextStatsVO", description = "文本统计结果")
public record TextStatsVO(
        @Schema(description = "字符数（含标点与空格）", example = "22") long characterCount,
        @Schema(description = "单词数", example = "3") long wordCount,
        @Schema(description = "去重后的单词数", example = "3") long uniqueWordCount,
        @Schema(description = "出现频率最高的单词（最多 10 个）") List<WordFrequencyVO> topWords
) {
}
