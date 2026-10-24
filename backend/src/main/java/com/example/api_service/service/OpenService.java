package com.example.api_service.service;

import com.example.api_service.model.vo.open.EchoVO;
import com.example.api_service.model.vo.open.TextStatsVO;

public interface OpenService {

    EchoVO echo(String message);

    TextStatsVO textStats(String text);
}
