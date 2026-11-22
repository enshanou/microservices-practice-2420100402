package com.zjgsu.oes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class ModelProfiles {
    record Profile(String provider, String label, String url, String key, String model, boolean claude) {}
    record Choice(String id, String label, String model) {}
    private final Map<String, Profile> profiles = new LinkedHashMap<>();

    ModelProfiles(Environment env) {
        add(env, "default", "默认模型", "NOTES_", "", false);
        add(env, "deepseek", "DeepSeek", "NOTES_DEEPSEEK_", "https://api.deepseek.com", false);
        add(env, "gpt", "GPT", "NOTES_GPT_", "https://api.openai.com/v1", false);
        add(env, "claude", "Claude", "NOTES_CLAUDE_", "https://api.anthropic.com/v1", true);
        add(env, "kimi", "Kimi", "NOTES_KIMI_", "", false);
    }

    private void add(Environment env, String provider, String label, String prefix, String fallback, boolean claude) {
        String url = env.getProperty(prefix + "API_BASE_URL", fallback).strip().replaceAll("/+$", "");
        if (url.isEmpty()) url = fallback;
        String key = env.getProperty(prefix + "API_KEY", "").strip();
        String model = env.getProperty(prefix + "MODEL", "").strip();
        if (!url.isEmpty() && !key.isEmpty() && !model.isEmpty() && model.length() <= 150) {
            profiles.put(provider, new Profile(provider, label, url, key, model, claude));
        }
    }

    String defaultId() {
        return profiles.keySet().stream().findFirst().orElse("default");
    }

    boolean configured() { return !profiles.isEmpty(); }

    List<Choice> choices() {
        return profiles.values().stream().map(p -> new Choice(p.provider(), p.label(), p.model())).toList();
    }

    Profile require(String id) {
        var profile = profiles.get("default".equals(id) ? defaultId() : id);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "所选模型尚未配置，请切换模型或检查配置");
        }
        return profile;
    }
}
