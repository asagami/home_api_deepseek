package com.example.api_service.service.impl;

import com.example.api_service.model.vo.open.EchoVO;
import com.example.api_service.model.vo.open.TextStatsVO;
import com.example.api_service.model.vo.open.WordFrequencyVO;
import com.example.api_service.service.OpenService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class OpenServiceImpl implements OpenService {

    /** 按 Unicode 字母/数字连续片段分词，兼顾英文与数字。 */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final int TOP_WORDS_LIMIT = 10;

    @Override
    public EchoVO echo(String message) {
        return new EchoVO(message, System.currentTimeMillis());
    }

    @Override
    public TextStatsVO textStats(String text) {
        List<String> tokens = TOKEN_PATTERN.matcher(text).results()
                .map(result -> result.group().toLowerCase(Locale.ROOT))
                .toList();

        Map<String, Long> frequencies = tokens.stream()
                .collect(Collectors.groupingBy(token -> token, Collectors.counting()));

        List<WordFrequencyVO> topWords = frequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_WORDS_LIMIT)
                .map(entry -> new WordFrequencyVO(entry.getKey(), entry.getValue()))
                .toList();

        return new TextStatsVO(
                text.length(),
                tokens.size(),
                frequencies.size(),
                topWords
        );
    }
}
