package com.railwindow.sim;

import com.fasterxml.jackson.databind.JsonNode;

final class JsonUtils {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private JsonUtils() {
    }

    static long readId(String responseBody) throws Exception {
        JsonNode node = MAPPER.readTree(responseBody);
        return node.get("id").asLong();
    }
}
