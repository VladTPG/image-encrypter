package com.imageencrypter.subscriber;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class StorageClient {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static int store(String storageHost, byte[] imageBytes,
                            String jobId, String operation, String mode,
                            String originalName, String userEmail) throws Exception {

        String url = String.format(
                "http://%s:3000/api/images?jobId=%s&operation=%s&mode=%s&originalName=%s&userEmail=%s",
                storageHost,
                enc(jobId), enc(operation), enc(mode), enc(originalName), enc(userEmail)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(imageBytes))
                .build();

        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Storage API returned " +
                    response.statusCode() + ": " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.get("id").getAsInt();
    }

    private static String enc(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
