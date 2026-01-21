package com.payment.paymentservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCrawlerService {

    private final ObjectMapper objectMapper;

    @Value("${crawler.python.path:python3}")
    private String pythonPath;

    @Value("${crawler.timeout-seconds:30}")
    private int timeoutSeconds;

    private Path cachedScriptPath;

    /**
     * URL에서 상품 정보를 크롤링
     *
     * @param url 상품 URL
     * @return 크롤링 결과
     */
    public CrawlResult crawlProduct(String url) {
        log.info("Starting product crawl for URL: {}", url);

        try {
            Path scriptPath = getScriptPath();
            ProcessBuilder processBuilder = new ProcessBuilder(
                pythonPath, scriptPath.toString(), url
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Crawler process timed out for URL: {}", url);
                return CrawlResult.failure("Crawler process timed out");
            }

            String jsonOutput = output.toString().trim();
            log.debug("Crawler output: {}", jsonOutput);

            return parseCrawlResult(jsonOutput, url);

        } catch (IOException e) {
            log.error("IO error during crawling: {}", e.getMessage(), e);
            return CrawlResult.failure("IO error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Crawler interrupted for URL: {}", url);
            return CrawlResult.failure("Crawler interrupted");
        } catch (Exception e) {
            log.error("Unexpected error during crawling: {}", e.getMessage(), e);
            return CrawlResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    private Path getScriptPath() throws IOException {
        if (cachedScriptPath != null && Files.exists(cachedScriptPath)) {
            return cachedScriptPath;
        }

        // 리소스에서 스크립트를 임시 파일로 복사
        try (InputStream is = getClass().getResourceAsStream("/scripts/product_crawler.py")) {
            if (is == null) {
                throw new IOException("Crawler script not found in resources");
            }

            Path tempScript = Files.createTempFile("product_crawler_", ".py");
            Files.copy(is, tempScript, StandardCopyOption.REPLACE_EXISTING);
            tempScript.toFile().deleteOnExit();
            cachedScriptPath = tempScript;

            log.info("Crawler script extracted to: {}", tempScript);
            return tempScript;
        }
    }

    private CrawlResult parseCrawlResult(String jsonOutput, String url) {
        try {
            JsonNode node = objectMapper.readTree(jsonOutput);

            boolean success = node.path("success").asBoolean(false);
            if (!success) {
                String error = node.path("error").asText("Unknown error");
                return CrawlResult.failure(error);
            }

            String productName = node.path("productName").asText(null);
            int productPrice = node.path("productPrice").asInt(0);
            String productImageUrl = node.path("productImageUrl").asText(null);
            String productDescription = node.path("productDescription").asText(null);
            String domain = node.path("domain").asText(null);

            return CrawlResult.success(
                productName,
                BigDecimal.valueOf(productPrice),
                productImageUrl,
                productDescription,
                url,
                domain
            );

        } catch (Exception e) {
            log.error("Failed to parse crawler output: {}", e.getMessage());
            return CrawlResult.failure("Failed to parse result: " + e.getMessage());
        }
    }

    /**
     * 크롤링 결과 DTO
     */
    public record CrawlResult(
        boolean success,
        String productName,
        BigDecimal productPrice,
        String productImageUrl,
        String productDescription,
        String productUrl,
        String domain,
        String error
    ) {
        public static CrawlResult success(String productName, BigDecimal productPrice,
                                           String productImageUrl, String productDescription,
                                           String productUrl, String domain) {
            return new CrawlResult(true, productName, productPrice, productImageUrl,
                productDescription, productUrl, domain, null);
        }

        public static CrawlResult failure(String error) {
            return new CrawlResult(false, null, null, null, null, null, null, error);
        }
    }
}
