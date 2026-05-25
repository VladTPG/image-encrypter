package com.imageencrypter.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequestMapping("/api/notifications")
public class NotificationController {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @GetMapping("/{jobId}")
    public SseEmitter subscribe(@PathVariable String jobId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        emitters.put(jobId, emitter);
        emitter.onCompletion(() -> emitters.remove(jobId));
        emitter.onTimeout(() -> emitters.remove(jobId));
        emitter.onError(e -> emitters.remove(jobId));

        return emitter;
    }

    public void notifyDone(String jobId, int imageId) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(Map.of(
                                "jobId", jobId,
                                "imageId", imageId,
                                "downloadUrl", "/api/images/" + imageId
                        )));
                emitter.complete();
            } catch (Exception e) {
                emitters.remove(jobId);
            }
        }
    }
}
