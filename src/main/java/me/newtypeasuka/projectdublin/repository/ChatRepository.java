package me.newtypeasuka.projectdublin.repository;

import jakarta.persistence.LockModeType;
import me.newtypeasuka.projectdublin.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT message FROM ChatMessage message "
            + "JOIN FETCH message.sender "
            + "ORDER BY message.id DESC")
    List<ChatMessage> findLatest(Pageable pageable);

    @Query("SELECT message FROM ChatMessage message "
            + "JOIN FETCH message.sender "
            + "WHERE message.id < :beforeId "
            + "ORDER BY message.id DESC")
    List<ChatMessage> findBeforeId(
            @Param("beforeId") Long beforeId,
            Pageable pageable
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
