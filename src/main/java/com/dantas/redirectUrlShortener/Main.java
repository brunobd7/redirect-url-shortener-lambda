package com.dantas.redirectUrlShortener;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class Main implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final String BUCKET_NAME = "dantas-url-shortener-storage-1";

    private final S3Client s3Client = S3Client.builder().build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> stringObjectMap, Context context) {

        String pathParam = (String) stringObjectMap.get("rawPath");
        String originalUrlCode = pathParam.replace("/", "");

        if (originalUrlCode == null || originalUrlCode.isEmpty())
            throw new IllegalArgumentException("Invalid URL code");

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(originalUrlCode.concat(".json"))
                .build();

        InputStream s3ObjectInputStream;
        try {
            s3ObjectInputStream = s3Client.getObject(getObjectRequest);

        } catch (Exception e) {
            throw new RuntimeException("Erro when trying to get object from S3 " + e.getMessage());
        }


        UrlData urlData;
        try {
            urlData = objectMapper.readValue(s3ObjectInputStream, UrlData.class);

        } catch (IOException e) {
            throw new RuntimeException("Erro when trying to parse s3 object to UrlData " + e.getMessage());
        }

        long currentTimeInSeconds = System.currentTimeMillis() / 1000;

        Map<String, Object> response = new HashMap<>();

        // Hanlde Expired URL scenario
        if (urlData.getExpirationTime() < currentTimeInSeconds) {
            response.put("statusCode", 410);
            response.put("body", "URL expired");
            return response;
        }

        // Happy Path  ->  USING REDIRECT HTTP + LOCATION HEADER to redirect
        response.put("statusCode", 302);

        Map<String, String> headers = new HashMap<>();
        headers.put("Location", urlData.getOriginalUrl());

        response.put("headers", headers);
        return response;
    }
}