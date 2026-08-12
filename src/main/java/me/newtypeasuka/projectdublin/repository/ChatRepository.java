package me.newtypeasuka.projectdublin.repository;

import jakarta.persistence.LockModeType;
import me.newtypeasuka.projectdublin.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT message FROM ChatMessage message "
            + "JOIN FETCH message.sender "
            + "WHERE message.createdAt >= :retentionCutoff "
            + "ORDER BY message.id DESC")
    List<ChatMessage> findLatest(
            @Param("retentionCutoff") LocalDateTime retentionCutoff,
            Pageable pageable
    );

    @Query("SELECT message FROM ChatMessage message "
            + "JOIN FETCH message.sender "
            + "WHERE message.id < :beforeId "
            + "AND message.createdAt >= :retentionCutoff "
            + "ORDER BY message.id DESC")
    List<ChatMessage> findBeforeId(
            @Param("beforeId") Long beforeId,
            @Param("retentionCutoff") LocalDateTime retentionCutoff,
            Pageable pageable
    );

    // 보존 기간이 지난 메시지를 한 번의 쿼리로 실제 삭제
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChatMessage message "
            + "WHERE message.createdAt < :retentionCutoff")
    int deleteExpiredMessages(
            @Param("retentionCutoff") LocalDateTime retentionCutoff
    );

    @Query("SELECT message FROM ChatMessage message "
            + "JOIN FETCH message.sender "
            + "WHERE message.sender.id = :senderId "
            + "AND message.clientMessageId = :clientMessageId")
    Optional<ChatMessage> findBySenderIdAndClientMessageId(
            @Param("senderId") Long senderId,
            @Param("clientMessageId") String clientMessageId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT message FROM ChatMessage message "
            + "JOIN FETCH message.sender "
            + "WHERE message.id = :messageId")
    Optional<ChatMessage> findByIdForUpdate(@Param("messageId") Long messageId);
}
