package it.mida.google.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GmailMessageParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GmailMessageParser parser = new GmailMessageParser();

    @Test
    void extractsHeadersTextAndNestedAttachment() {
        String subject = encoded("Compiti di matematica");
        String text = encoded("Ciao dal corpo");
        String html = encoded("<p>Corpo HTML</p>");
        String json = """
                {
                  "id": "message-1",
                  "threadId": "thread-1",
                  "snippet": "Anteprima del messaggio",
                  "internalDate": "1763000000000",
                  "labelIds": ["INBOX", "UNREAD"],
                  "payload": {
                    "mimeType": "multipart/mixed",
                    "headers": [
                      {"name": "From", "value": "DidUP Family <didup@example.com>"},
                      {"name": "To", "value": "studente@example.com"},
                      {"name": "Cc", "value": "genitore@example.com"},
                      {"name": "Subject", "value": "=?UTF-8?B?%s?="}
                    ],
                    "parts": [
                      {
                        "mimeType": "multipart/alternative",
                        "parts": [
                          {
                            "mimeType": "text/plain",
                            "headers": [{"name": "Content-Type", "value": "text/plain; charset=UTF-8"}],
                            "body": {"data": "%s"}
                          },
                          {
                            "mimeType": "text/html",
                            "headers": [{"name": "Content-Type", "value": "text/html; charset=UTF-8"}],
                            "body": {"data": "%s"}
                          }
                        ]
                      },
                      {
                        "mimeType": "text/plain",
                        "filename": "text-ABCDEF123456.txt",
                        "headers": [{"name": "Content-Disposition", "value": "attachment; filename=text-ABCDEF123456.txt"}],
                        "body": {"attachmentId": "attachment-1", "size": 42}
                      }
                    ]
                  }
                }
                """.formatted(subject, text, html);

        GmailMessageParser.ParsedGmailMessage parsed = parser.parse(objectMapper.readTree(json));

        assertThat(parsed.gmailId()).isEqualTo("message-1");
        assertThat(parsed.threadId()).isEqualTo("thread-1");
        assertThat(parsed.from()).isEqualTo("DidUP Family <didup@example.com>");
        assertThat(parsed.to()).isEqualTo("studente@example.com");
        assertThat(parsed.cc()).isEqualTo("genitore@example.com");
        assertThat(parsed.subject()).isEqualTo("Compiti di matematica");
        assertThat(parsed.bodyText()).isEqualTo("Ciao dal corpo");
        assertThat(parsed.receivedAt()).isEqualTo(Instant.ofEpochMilli(1763000000000L));
        assertThat(parsed.labels()).containsExactly("INBOX", "UNREAD");
        assertThat(parsed.attachments()).hasSize(1);
        assertThat(parsed.attachments().getFirst().providerAttachmentId()).isEqualTo("attachment-1");
        assertThat(parsed.attachments().getFirst().filename()).isEqualTo("text-ABCDEF123456.txt");
        assertThat(parsed.attachments().getFirst().size()).isEqualTo(42);
        assertThat(parsed.attachments().getFirst().data()).isNull();
    }

    @Test
    void fallsBackToHtmlWhenNoPlainTextIsPresent() {
        String json = """
                {
                  "id": "message-2",
                  "snippet": "Anteprima",
                  "payload": {
                    "mimeType": "text/html",
                    "headers": [
                      {"name": "From", "value": "didup@example.com"},
                      {"name": "Subject", "value": "Solo HTML"}
                    ],
                    "body": {"data": "%s"}
                  }
                }
                """.formatted(encoded("<p>Contenuto <strong>HTML</strong></p>"));

        GmailMessageParser.ParsedGmailMessage parsed = parser.parse(objectMapper.readTree(json));

        assertThat(parsed.bodyText()).contains("Contenuto").contains("HTML");
        assertThat(parsed.attachments()).isEmpty();
    }

    private String encoded(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
