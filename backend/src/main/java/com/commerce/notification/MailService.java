package com.commerce.notification;

import com.commerce.common.exception.EmailDeliveryException;
import com.commerce.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Outbound SMTP adapter. In the local profile mail can be logged instead of sent (never in production). */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private final JavaMailSender sender;
    private final AppProperties props;

    public MailService(JavaMailSender sender, AppProperties props) {
        this.sender = sender;
        this.props = props;
    }

    public void send(String to, String subject, String body) {
        if (props.getMail().isLogOnly()) {
            log.info("[DEV MAIL - not sent] to={} subject={}\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.getMail().getFromEmail());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
        } catch (MailException ex) {
            // translate low-level failure into a domain exception (no secrets in the message)
            log.warn("SMTP delivery failed: {}", ex.getClass().getSimpleName());
            throw new EmailDeliveryException();
        }
    }
}
