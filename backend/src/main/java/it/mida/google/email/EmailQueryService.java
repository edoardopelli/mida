package it.mida.google.email;

import it.mida.cluster.ConditionalOnMidaRole;
import it.mida.cluster.MidaRole;
import it.mida.common.MidaNotFoundException;
import it.mida.common.PageResponse;
import it.mida.google.email.api.EmailAttachmentDto;
import it.mida.google.email.api.EmailMessageSummaryDto;
import it.mida.google.email.api.EmailMessageView;
import it.mida.google.email.api.EmailQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnMidaRole({MidaRole.STANDALONE, MidaRole.MASTER})
public class EmailQueryService {

    private final MongoTemplate mongoTemplate;
    private final EmailMessageRepository messageRepository;
    private final EmailAttachmentRepository attachmentRepository;

    public EmailQueryService(
            MongoTemplate mongoTemplate,
            EmailMessageRepository messageRepository,
            EmailAttachmentRepository attachmentRepository) {
        this.mongoTemplate = mongoTemplate;
        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
    }

    public PageResponse<EmailMessageSummaryDto> search(EmailQuery query) {
        Query mongoQuery = new Query(criteria(query));
        long total = mongoTemplate.count(mongoQuery, EmailMessage.class);
        mongoQuery.with(Sort.by(
                Sort.Order.desc("receivedAt"),
                Sort.Order.desc("id")));
        mongoQuery.skip((long) query.page() * query.size());
        mongoQuery.limit(query.size());
        List<EmailMessageSummaryDto> content = mongoTemplate
                .find(mongoQuery, EmailMessage.class).stream()
                .map(this::toSummary)
                .toList();
        return PageResponse.of(content, query.page(), query.size(), total);
    }

    public EmailMessageView findById(String id) {
        EmailMessage message = messageRepository.findById(id)
                .orElseThrow(() -> new MidaNotFoundException("Email non trovata"));
        List<EmailAttachmentDto> attachments = attachmentRepository
                .findByEmailMessageIdOrderByFilenameAsc(id).stream()
                .map(this::toAttachment)
                .toList();
        return new EmailMessageView(
                message.getId(),
                message.getFrom(),
                message.getTo(),
                message.getCc(),
                message.getSubject(),
                message.getBodyText(),
                message.getReceivedAt(),
                message.getLabels() == null ? List.of() : List.copyOf(message.getLabels()),
                attachments);
    }

    private Criteria criteria(EmailQuery query) {
        List<Criteria> criteria = new ArrayList<>();
        if (StringUtils.hasText(query.from())) {
            criteria.add(Criteria.where("from").regex(pattern(query.from())));
        }
        if (StringUtils.hasText(query.q())) {
            Pattern pattern = pattern(query.q());
            criteria.add(new Criteria().orOperator(
                    Criteria.where("subject").regex(pattern),
                    Criteria.where("from").regex(pattern),
                    Criteria.where("bodyText").regex(pattern)));
        }
        return criteria.isEmpty() ? new Criteria() : new Criteria().andOperator(criteria);
    }

    private Pattern pattern(String value) {
        return Pattern.compile(
                Pattern.quote(value.strip()),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private EmailMessageSummaryDto toSummary(EmailMessage message) {
        return new EmailMessageSummaryDto(
                message.getId(),
                message.getFrom(),
                message.getSubject(),
                message.getSnippet(),
                message.getReceivedAt(),
                message.isHasAttachments(),
                message.getAttachmentCount());
    }

    private EmailAttachmentDto toAttachment(EmailAttachment attachment) {
        String downloadUrl = StringUtils.hasText(attachment.getGridFsId())
                ? "/api/attachments/" + attachment.getGridFsId()
                : null;
        return new EmailAttachmentDto(
                attachment.getId(),
                attachment.getFilename(),
                attachment.getMimeType(),
                attachment.getSize(),
                attachment.isInline(),
                attachment.getImportStatus(),
                attachment.getImportMessage(),
                downloadUrl);
    }
}
