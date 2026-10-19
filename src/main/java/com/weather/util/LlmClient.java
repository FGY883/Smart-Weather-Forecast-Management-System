package com.weather.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容的大模型对话客户端（base-url + /chat/completions + Bearer 鉴权）。
 * 仅负责一次同步补全；密钥来自配置（本地 application-local.yml / 环境变量），不硬编码、不外泄到日志。
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    @Value("${llm.enabled:true}")
    private boolean enabled;
    @Value("${llm.base-url:}")
    private String baseUrl;
    @Value("${llm.api-key:}")
    private String apiKey;
    @Value("${llm.model:}")
    private String model;
    @Value("${llm.timeout-seconds:20}")
    private int timeoutSeconds;
    @Value("${llm.max-tokens:500}")
    private int maxTokens;

    private CloseableHttpClient httpClient;

    @PostConstruct
    public void init() {
        int ms = Math.max(1, timeoutSeconds) * 1000;
        RequestConfig rc = RequestConfig.custom()
                .setConnectTimeout(ms).setSocketTimeout(ms).setConnectionRequestTimeout(ms).build();
        this.httpClient = HttpClients.custom().setDefaultRequestConfig(rc).build();
    }

    public boolean isEnabled() {
        return enabled && StringUtils.hasText(baseUrl) && StringUtils.hasText(apiKey) && StringUtils.hasText(model);
    }

    /**
     * 发送对话消息，返回助手回复文本。失败抛 IOException 交由调用方回退。
     *
     * @param messages 形如 [{role:system|user|assistant, content:...}, ...]
     */
    public String chat(List<Map<String, String>> messages) throws IOException {
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", 0.2);
        payload.put("max_tokens", maxTokens);
        payload.put("stream", false);

        String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        HttpPost post = new HttpPost(url);
        post.setHeader("Authorization", "Bearer " + apiKey);
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(payload.toJSONString(), ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse resp = httpClient.execute(post)) {
            int status = resp.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(resp.getEntity(), "UTF-8");
            if (status != 200) {
                // 不回显响应体全文，避免敏感信息；仅记状态与截断片段
                log.warn("大模型接口返回非 200：status={}, body={}", status, abbreviate(body));
                throw new IOException("LLM http status " + status);
            }
            JSONObject json = JSON.parseObject(body);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IOException("LLM empty choices");
            }
            JSONObject msg = choices.getJSONObject(0).getJSONObject("message");
            String content = msg != null ? msg.getString("content") : null;
            if (!StringUtils.hasText(content)) {
                throw new IOException("LLM empty content");
            }
            return content.trim();
        }
    }

    private String abbreviate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
