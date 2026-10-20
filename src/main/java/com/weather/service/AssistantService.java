package com.weather.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.weather.util.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统使用助手。
 *
 * 优先调用大模型（OpenAI 兼容），并以 persona.systemPrompt 约束角色、
 * 以命中的本地知识库主题为「参考资料」注入提示词（检索增强 / RAG），
 * 使模型基于本系统真实功能作答；当未配置模型或调用失败时，回退为
 * 纯检索式关键词问答，保证离线仍可演示。
 *
 * 角色设定与知识库定义在 classpath:assistant/knowledge.json。
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final String KB_PATH = "assistant/knowledge.json";
    /** 注入模型的历史对话最大条数（不含本次问题） */
    private static final int MAX_HISTORY = 6;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private CityWeatherService cityWeatherService;

    private String personaName = "小天";
    private String greeting = "";
    private String systemPrompt = "";
    private String fallback = "我可以解答本系统的使用与功能问题。";
    private List<String> suggestions = new ArrayList<>();
    private final List<Topic> topics = new ArrayList<>();

    private static final class Topic {
        String id;
        List<String> keywords;
        String answer;
    }

    @PostConstruct
    public void load() {
        try {
            String raw = StreamUtils.copyToString(new ClassPathResource(KB_PATH).getInputStream(), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(raw);

            JSONObject persona = root.getJSONObject("persona");
            if (persona != null) {
                personaName = persona.getString("name") != null ? persona.getString("name") : personaName;
                greeting = persona.getString("greeting") != null ? persona.getString("greeting") : "";
                systemPrompt = persona.getString("systemPrompt") != null ? persona.getString("systemPrompt") : "";
            }
            if (root.getString("fallback") != null) fallback = root.getString("fallback");
            JSONArray sug = root.getJSONArray("suggestions");
            if (sug != null) suggestions = sug.toJavaList(String.class);

            JSONArray tps = root.getJSONArray("topics");
            if (tps != null) {
                for (int i = 0; i < tps.size(); i++) {
                    JSONObject t = tps.getJSONObject(i);
                    Topic topic = new Topic();
                    topic.id = t.getString("id");
                    topic.answer = t.getString("answer");
                    JSONArray kw = t.getJSONArray("keywords");
                    topic.keywords = kw != null ? kw.toJavaList(String.class) : new ArrayList<>();
                    topics.add(topic);
                }
            }
            log.info("助手知识库加载完成：{} 个主题；大模型{}（model 未配置则走检索式回退）",
                    topics.size(), llmClient.isEnabled() ? "已启用" : "未启用");
        } catch (Exception e) {
            log.error("加载助手知识库失败，助手将使用兜底回复", e);
        }
    }

    public Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", personaName);
        m.put("greeting", greeting);
        m.put("suggestions", suggestions);
        return m;
    }

    /**
     * 回答用户问题。history 为既往对话 [{role,content}, ...]（可空）。
     */
    public Map<String, Object> answer(String question, List<Map<String, String>> history) {
        // 城市天气识别（优先内置目录，其次按天气意图解析城市）
        CityWeatherService.CityHit hit = cityWeatherService.detectCity(question);
        String weatherDigest = hit != null ? cityWeatherService.buildDigest(hit) : null;
        boolean weatherIntentNoCity = (hit == null && cityWeatherService.hasWeatherKeyword(question));
        Match usage = pickTopic(question);
        String supportedHint = weatherIntentNoCity ? cityWeatherService.supportedCityHint() : null;

        if (llmClient.isEnabled()) {
            try {
                List<Map<String, String>> messages = new ArrayList<>();
                messages.add(msg("system", buildSystemPrompt(usage.topic, weatherDigest, supportedHint)));
                if (history != null) {
                    int start = Math.max(0, history.size() - MAX_HISTORY);
                    for (int i = start; i < history.size(); i++) {
                        Map<String, String> h = history.get(i);
                        String role = h.get("role");
                        String content = clip(h.get("content"), 500);   // 限制历史单条长度，控输入 token
                        if (("user".equals(role) || "assistant".equals(role)) && content != null && !content.isEmpty()) {
                            messages.add(msg(role, content));
                        }
                    }
                }
                messages.add(msg("user", clip(question, 1000)));        // 用户问题限长，防超长输入放大计费
                String reply = llmClient.chat(messages);

                Map<String, Object> out = new LinkedHashMap<>();
                out.put("name", personaName);
                out.put("reply", reply);
                out.put("matched", weatherDigest != null ? "weather:" + hit.name
                        : (usage.topic != null ? usage.topic.id : null));
                out.put("confidence", 0.9);
                out.put("source", "llm");
                out.put("suggestions", suggestions);
                return out;
            } catch (Exception e) {
                log.warn("大模型调用失败，回退到本地知识/天气检索：{}", e.getMessage());
            }
        }

        // 回退（未启用/调用失败）：天气摘要 > 功能知识库 > 支持城市提示 > 兜底
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", personaName);
        if (weatherDigest != null) {
            out.put("reply", weatherDigest + "\n（以上为系统实时数据，如需我口语化解读可开启智能助手大模型。）");
            out.put("matched", "weather:" + hit.name);
            out.put("confidence", 0.85);
            out.put("source", "weather");
        } else if (usage.topic != null) {
            out.put("reply", usage.topic.answer);
            out.put("matched", usage.topic.id);
            out.put("confidence", Math.min(0.55 + 0.15 * usage.score, 0.99));
            out.put("source", "kb");
        } else if (weatherIntentNoCity) {
            out.put("reply", supportedHint);
            out.put("matched", null);
            out.put("confidence", 0.5);
            out.put("source", "kb");
        } else {
            out.put("reply", fallback);
            out.put("matched", null);
            out.put("confidence", 0.2);
            out.put("source", "kb");
        }
        out.put("suggestions", suggestions);
        return out;
    }

    /** 角色设定 + 命中系统知识 + 实时天气资料 + 支持城市提示，拼装为模型系统提示（检索增强）。 */
    private String buildSystemPrompt(Topic usageRef, String weatherDigest, String supportedHint) {
        StringBuilder sb = new StringBuilder(systemPrompt);
        if (weatherDigest != null) {
            sb.append("\n\n【参考资料：城市实时/预报天气数据（系统抓取，请据此回答该城市天气，不要编造数值）】\n")
              .append(weatherDigest);
        }
        if (usageRef != null) {
            sb.append("\n\n【参考资料：本系统功能知识库】\n主题：").append(usageRef.id)
              .append("\n内容：").append(usageRef.answer);
        }
        if (supportedHint != null) {
            sb.append("\n\n【提示】用户像在问某城市天气但未能识别城市，可告知：").append(supportedHint);
        }
        sb.append("\n\n回答要简洁友好（3-5 句）。若无相应参考资料支撑，不要臆造实时天气数值或系统不存在的功能。");
        return sb.toString();
    }

    private Map<String, String> msg(String role, String content) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("role", role);
        map.put("content", content);
        return map;
    }

    /** 截断过长文本，限制进入大模型的输入 token。 */
    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static final class Match {
        Topic topic;
        int score;
    }

    /** 按关键词命中数挑选最相关主题。 */
    private Match pickTopic(String question) {
        String q = normalize(question);
        Match best = new Match();
        if (!q.isEmpty()) {
            for (Topic t : topics) {
                int score = 0;
                for (String kw : t.keywords) {
                    String nkw = normalize(kw);
                    if (!nkw.isEmpty() && q.contains(nkw)) score++;
                }
                if (score > best.score) {
                    best.score = score;
                    best.topic = t;
                }
            }
        }
        return best;
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", "").toLowerCase();
    }
}
