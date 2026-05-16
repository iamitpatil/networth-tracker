package com.networth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;

    public void sendSIPReminder(String email, String symbol, BigDecimal amount, LocalDate dueDate) {
        String subject = "SIP Reminder: " + symbol;
        String body = String.format(
                "Your SIP of Rs. %,.2f for %s is due on %s.\n\n" +
                "Ensure sufficient balance in your linked account.",
                amount, symbol, dueDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        );

        sendEmail(email, subject, body);
    }

    public void sendEMIReminder(String email, String lender, BigDecimal emi, LocalDate dueDate) {
        String subject = "EMI Due: " + lender;
        String body = String.format(
                "Your EMI of Rs. %,.2f for %s is due on %s.\n\n" +
                "Ensure timely payment to avoid penalties.",
                emi, lender, dueDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        );

        sendEmail(email, subject, body);
    }

    public void sendTaxHarvestAlert(String email, String symbol, BigDecimal potentialSavings) {
        String subject = "Tax Harvesting Opportunity: " + symbol;
        String body = String.format(
                "You can save Rs. %,.2f in taxes by harvesting gains on %s.\n\n" +
                "Review your tax harvesting opportunities in the app.",
                potentialSavings, symbol
        );

        sendEmail(email, subject, body);
    }

    public void sendPriceAlert(String email, String symbol, BigDecimal price, String type) {
        String subject = "Price Alert: " + symbol;
        String body = String.format(
                "%s has reached Rs. %,.2f.\n\n" +
                "Type: %s",
                symbol, price, type
        );

        sendEmail(email, subject, body);
    }

    public void sendWeeklyNetWorthSummary(String email, BigDecimal netWorth, BigDecimal change) {
        String subject = "Weekly Net Worth Update";
        String direction = change.compareTo(BigDecimal.ZERO) >= 0 ? "increased" : "decreased";
        String body = String.format(
                "Your net worth is Rs. %,.2f this week.\n" +
                "It has %s by Rs. %,.2f.\n\n" +
                "Log in to see detailed breakdown.",
                netWorth, direction, change.abs()
        );

        sendEmail(email, subject, body);
    }

    public void sendEmail(String to, String subject, String body) {
        sendEmailMessage(to, subject, body);
    }

    private void sendEmailMessage(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {} - {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
