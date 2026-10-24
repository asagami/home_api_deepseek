package com.example.api_service.engine.handlers.geo;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpGeoLocateApiHandlerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final IpGeoLocateApiHandler handler =
            new IpGeoLocateApiHandler(objectMapper, "https://ipapi.co/");

    @Test
    void shouldBuildGetUrlAndHeaders() {
        IpLocateReq request = new IpLocateReq();
        request.setIp(" 8.8.8.8 ");

        assertEquals("https://ipapi.co/8.8.8.8/json/", handler.buildTargetUrl(request));
        assertEquals("application/json", handler.buildHeaders().get("Accept"));
    }

    @Test
    void shouldCleanUpstreamResponseIntoStandardVo() {
        String rawJson = """
                {
                  "ip": "8.8.8.8",
                  "country_name": "United States",
                  "region": "California",
                  "city": "Mountain View",
                  "org": "Google LLC",
                  "latitude": 37.4056,
                  "longitude": -122.0775,
                  "timezone": "America/Los_Angeles"
                }
                """;

        IpLocateVO result = handler.transformResponse(rawJson);

        assertEquals("8.8.8.8", result.ip());
        assertEquals("United States", result.country());
        assertEquals("California", result.region());
        assertEquals("Mountain View", result.city());
        assertEquals("Google LLC", result.isp());
        assertEquals(37.4056d, result.latitude(), 1e-9);
        assertEquals(-122.0775d, result.longitude(), 1e-9);
        assertEquals("America/Los_Angeles", result.timezone());
    }

    @Test
    void shouldTranslateUpstreamErrorIntoSingleUpstreamCode() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> handler.transformResponse("{\"error\":true,\"reason\":\"Invalid IP Address\"}"));

        assertEquals(ErrorCode.UPSTREAM_ERROR, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Invalid IP Address"));
    }

    @Test
    void shouldRejectMalformedUpstreamJson() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> handler.transformResponse("not-json"));

        assertEquals(ErrorCode.UPSTREAM_ERROR, exception.getErrorCode());
    }
}
