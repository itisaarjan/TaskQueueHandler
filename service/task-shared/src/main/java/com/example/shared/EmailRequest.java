package com.example.shared;

public class EmailRequest {
    private String to;
    private String subject;
    private String body;
    private boolean html;

    public EmailRequest() {}

    public EmailRequest(final String to, final String subject, final String body, final boolean html) {
        this.to = to;
        this.subject = subject;
        this.body = body;
        this.html = html;
    }

    public String getTo() { return to; }
    public void setTo(final String to) { this.to = to; }

    public String getSubject() { return subject; }
    public void setSubject(final String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(final String body) { this.body = body; }

    public boolean isHtml() { return html; }
    public void setHtml(final boolean html) { this.html = html; }
}
