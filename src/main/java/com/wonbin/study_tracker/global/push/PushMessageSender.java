package com.wonbin.study_tracker.global.push;

import java.util.Map;

public interface PushMessageSender {
    void send(String token, Map<String, String> data);
}
