package com.example.notify.repository;

import com.example.notify.domain.NotifyMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotifyMessageRepository extends JpaRepository<NotifyMessage, Long> {}
