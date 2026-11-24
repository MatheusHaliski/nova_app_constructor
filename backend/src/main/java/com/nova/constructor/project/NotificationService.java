package com.nova.constructor.project;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Avisos dentro do app (sino da barra superior): estrela, fork, comentário e moderação. */
@Service
public class NotificationService {

    public enum Kind { STAR, FORK, COMMENT, MODERATION }

    public record Notification(long id, Kind kind, String message, String link, boolean read, Instant createdAt) {}

    private final JdbcClient jdbc;
    private final Clock clock;

    public NotificationService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void notify(UUID userId, Kind kind, String message, String link) {
        String text = message.length() <= 300 ? message : message.substring(0, 299) + "…";
        jdbc.sql("insert into notifications (user_id, kind, message, link, created_at) values (?, ?, ?, ?, ?)")
                .params(userId.toString(), kind.name(), text, link, Timestamp.from(clock.instant())).update();
    }

    public List<Notification> list(UUID userId) {
        return jdbc.sql("select id, kind, message, link, read_at, created_at from notifications where user_id = ? order by created_at desc, id desc limit 100")
                .param(userId.toString())
                .query((rs, n) -> new Notification(rs.getLong("id"), Kind.valueOf(rs.getString("kind")), rs.getString("message"),
                        rs.getString("link"), rs.getTimestamp("read_at") != null, rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    public long unread(UUID userId) {
        return jdbc.sql("select count(*) from notifications where user_id = ? and read_at is null").param(userId.toString()).query(Long.class).single();
    }

    /** Marca uma notificação (ou todas, sem id) como lida. */
    @Transactional
    public void markRead(UUID userId, Long id) {
        Timestamp now = Timestamp.from(clock.instant());
        if (id == null) {
            jdbc.sql("update notifications set read_at = ? where user_id = ? and read_at is null").params(now, userId.toString()).update();
        } else {
            jdbc.sql("update notifications set read_at = ? where user_id = ? and id = ? and read_at is null").params(now, userId.toString(), id).update();
        }
    }
}
