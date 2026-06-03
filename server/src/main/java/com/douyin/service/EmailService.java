package com.douyin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    /** 内存存储验证码, key=email, value=code */
    private final Map<String, CodeEntry> codeStore = new ConcurrentHashMap<>();

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendCode(String to) {
        String code = String.format("%06d", new Random().nextInt(1000000));
        codeStore.put(to, new CodeEntry(code, System.currentTimeMillis()));

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject("抖音验证码");
        msg.setText("您的验证码是：" + code + "，有效期5分钟。");
        mailSender.send(msg);
    }

    /** 验证并返回是否通过, 通过后删除验证码 */
    public boolean verify(String email, String code) {
        CodeEntry entry = codeStore.get(email);
        if (entry == null) return false;
        if (System.currentTimeMillis() - entry.ts > 5 * 60 * 1000) {
            codeStore.remove(email);
            return false;
        }
        if (entry.code.equals(code)) {
            codeStore.remove(email);
            return true;
        }
        return false;
    }

    private record CodeEntry(String code, long ts) {}
}
