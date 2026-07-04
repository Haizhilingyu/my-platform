package com.example.notify.repository;

import com.example.notify.domain.NotifyRecipient;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotifyRecipientRepository extends JpaRepository<NotifyRecipient, Long> {

  List<NotifyRecipient> findByMessageId(Long messageId);
}
