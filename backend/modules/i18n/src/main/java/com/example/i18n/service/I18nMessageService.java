package com.example.i18n.service;

import com.example.common.exception.BizException;
import com.example.common.exception.NotFoundException;
import com.example.common.result.PageResult;
import com.example.i18n.domain.I18nMessage;
import com.example.i18n.dto.I18nMessageImportDTO;
import com.example.i18n.dto.I18nMessageQueryDTO;
import com.example.i18n.dto.I18nMessageUpdateDTO;
import com.example.i18n.dto.I18nMessageVO;
import com.example.i18n.event.I18nMessageUpdatedEvent;
import com.example.i18n.repository.I18nMessageRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class I18nMessageService {

  private final I18nMessageRepository repository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public PageResult<I18nMessageVO> list(I18nMessageQueryDTO q) {
    Specification<I18nMessage> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (q.getLocale() != null && !q.getLocale().isBlank()) {
            predicates.add(cb.equal(root.get("locale"), q.getLocale()));
          }
          if (q.getModule() != null && !q.getModule().isBlank()) {
            predicates.add(cb.equal(root.get("module"), q.getModule()));
          }
          if (q.getKeyLike() != null && !q.getKeyLike().isBlank()) {
            predicates.add(cb.like(root.get("messageKey"), "%" + q.getKeyLike() + "%"));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };
    int pageNum = q.getPageNum() == null || q.getPageNum() < 1 ? 1 : q.getPageNum();
    int pageSize = q.getPageSize() == null || q.getPageSize() < 1 ? 20 : q.getPageSize();
    PageRequest pageable =
        PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.ASC, "messageKey"));
    Page<I18nMessage> page = repository.findAll(spec, pageable);
    List<I18nMessageVO> list = page.getContent().stream().map(I18nMessageVO::of).toList();
    return PageResult.of(list, page.getTotalElements(), pageNum, pageSize);
  }

  @Transactional
  public I18nMessageVO update(Long id, I18nMessageUpdateDTO dto) {
    I18nMessage message =
        repository
            .findById(id)
            .orElseThrow(() -> NotFoundException.i18n("i18n.message.not.found", id));
    message.setValue(dto.getValue());
    I18nMessage saved = repository.save(message);
    eventPublisher.publishEvent(new I18nMessageUpdatedEvent(this, saved.getLocale()));
    return I18nMessageVO.of(saved);
  }

  @Transactional
  public int importMessages(I18nMessageImportDTO dto) {
    List<I18nMessage> existing = repository.findByLocale(dto.getLocale());
    Map<String, I18nMessage> existingByKey =
        existing.stream()
            .collect(
                Collectors.toMap(
                    I18nMessage::getMessageKey, m -> m, (a, b) -> a, LinkedHashMap::new));
    Set<String> knownKeys = existingByKey.keySet();

    List<String> unknownKeys =
        dto.getItems().stream()
            .map(I18nMessageImportDTO.Item::getMessageKey)
            .filter(k -> !knownKeys.contains(k))
            .distinct()
            .toList();
    if (!unknownKeys.isEmpty()) {
      throw BizException.i18n(400, "i18n.import.unknown.keys", String.join(", ", unknownKeys));
    }

    int updated = 0;
    for (I18nMessageImportDTO.Item item : dto.getItems()) {
      I18nMessage target = existingByKey.get(item.getMessageKey());
      if (target != null && !item.getValue().equals(target.getValue())) {
        target.setValue(item.getValue());
        repository.save(target);
        updated++;
      }
    }
    eventPublisher.publishEvent(new I18nMessageUpdatedEvent(this, dto.getLocale()));
    return updated;
  }

  @Transactional(readOnly = true)
  public List<I18nMessageVO> exportByLocale(String locale) {
    return repository.findByLocale(locale).stream().map(I18nMessageVO::of).toList();
  }

  @Transactional(readOnly = true)
  public Map<String, String> getFlatMap(String locale) {
    return repository.findByLocale(locale).stream()
        .collect(
            Collectors.toMap(
                I18nMessage::getMessageKey,
                I18nMessage::getValue,
                (a, b) -> a,
                LinkedHashMap::new));
  }
}
