package com.example.notify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.example.notify.domain.NotifyMessage;
import com.example.notify.domain.NotifyUserInbox;
import com.example.notify.dto.PublishDTO;
import com.example.notify.enums.MessageLevel;
import com.example.notify.enums.RecipientType;
import com.example.notify.repository.NotifyMessageRepository;
import com.example.notify.repository.NotifyRecipientRepository;
import com.example.notify.repository.NotifyUserInboxRepository;
import com.example.sys.domain.SysUser;
import com.example.sys.domain.SysUserRole;
import com.example.sys.repository.SysUnitRepository;
import com.example.sys.repository.SysUserRepository;
import com.example.sys.repository.SysUserRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
@DisplayName("消息发布服务")
class MessageServiceTest {

  @Mock private NotifyMessageRepository messageRepository;
  @Mock private NotifyRecipientRepository recipientRepository;
  @Mock private NotifyUserInboxRepository inboxRepository;
  @Mock private SysUserRepository sysUserRepository;
  @Mock private SysUserRoleRepository sysUserRoleRepository;
  @Mock private SysUnitRepository sysUnitRepository;
  @Mock private WebSocketSessionRegistry sessionRegistry;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks private MessageService messageService;

  @BeforeEach
  void setUp() {
    when(messageRepository.save(any(NotifyMessage.class)))
        .thenAnswer(
            inv -> {
              NotifyMessage m = inv.getArgument(0);
              m.setId(100L);
              return m;
            });
    when(inboxRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  @DisplayName("USER 范围：单用户发布，写入收件箱，seq=1")
  void should_publishToSingleUser_when_userScope() {
    PublishDTO dto = buildDto(MessageLevel.NORMAL, RecipientType.USER, 7L);
    when(inboxRepository.findMaxSeqByUserId(7L)).thenReturn(0L);

    MessageService.PublishResult result = messageService.publish(dto, 1L);

    assertThat(result.messageId()).isEqualTo(100L);
    assertThat(result.recipientCount()).isEqualTo(1);

    ArgumentCaptor<List<NotifyUserInbox>> captor = captorInboxes();
    verify(inboxRepository).saveAll(captor.capture());
    NotifyUserInbox inbox = captor.getValue().get(0);
    assertThat(inbox.getUserId()).isEqualTo(7L);
    assertThat(inbox.getMessageId()).isEqualTo(100L);
    assertThat(inbox.getSeq()).isEqualTo(1L);
    verify(recipientRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("ROLE 范围：展开所有持有该角色的用户")
  void should_expandToRoleUsers_when_roleScope() {
    PublishDTO dto = buildDto(MessageLevel.NORMAL, RecipientType.ROLE, 50L);
    when(sysUserRoleRepository.findByRoleId(50L))
        .thenReturn(List.of(new SysUserRole(11L, 50L), new SysUserRole(12L, 50L)));
    when(inboxRepository.findMaxSeqByUserId(anyLong())).thenReturn(0L);

    MessageService.PublishResult result = messageService.publish(dto, 1L);

    assertThat(result.recipientCount()).isEqualTo(2);
    verify(inboxRepository, times(1)).saveAll(anyList());
  }

  @Test
  @DisplayName("UNIT 范围：递归展开单位及下级所有用户")
  void should_expandToUnitDescendantUsers_when_unitScope() {
    PublishDTO dto = buildDto(MessageLevel.NORMAL, RecipientType.UNIT, 5L);
    when(sysUnitRepository.findDescendantUnitIds(5L)).thenReturn(java.util.Set.of(5L, 6L, 7L));
    SysUser u1 = SysUser.builder().id(21L).build();
    u1.setUnitId(5L);
    SysUser u2 = SysUser.builder().id(22L).build();
    u2.setUnitId(6L);
    SysUser u3 = SysUser.builder().id(23L).build();
    u3.setUnitId(7L);
    when(sysUserRepository.findByUnitIdIn(java.util.Set.of(5L, 6L, 7L)))
        .thenReturn(List.of(u1, u2, u3));
    when(inboxRepository.findMaxSeqByUserId(anyLong())).thenReturn(0L);

    MessageService.PublishResult result = messageService.publish(dto, 1L);

    assertThat(result.recipientCount()).isEqualTo(3);
    verify(sysUnitRepository).findDescendantUnitIds(5L);
    verify(sysUserRepository).findByUnitIdIn(java.util.Set.of(5L, 6L, 7L));
  }

  @Test
  @DisplayName("URGENT 级别：写入收件箱后立即推送 WebSocket 消息")
  void should_pushUrgent_when_levelIsUrgent() throws Exception {
    PublishDTO dto = buildDto(MessageLevel.URGENT, RecipientType.USER, 7L);
    when(inboxRepository.findMaxSeqByUserId(7L)).thenReturn(0L);
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.isOpen()).thenReturn(true);
    when(sessionRegistry.getSessions(7L)).thenReturn(java.util.Set.of(session));
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");

    messageService.publish(dto, 1L);

    verify(session, atLeastOnce()).sendMessage(any());
  }

  @Test
  @DisplayName("NORMAL 级别：仅写入收件箱，不推送 WebSocket")
  void should_notPush_when_levelIsNormal() {
    PublishDTO dto = buildDto(MessageLevel.NORMAL, RecipientType.USER, 7L);
    when(inboxRepository.findMaxSeqByUserId(7L)).thenReturn(0L);

    messageService.publish(dto, 1L);

    verify(sessionRegistry, never()).getSessions(anyLong());
  }

  @Test
  @DisplayName("seq 单调递增：用户已有 seq=5，新消息 seq=6")
  void should_incrementSeq_when_userHasHistory() {
    PublishDTO dto = buildDto(MessageLevel.NORMAL, RecipientType.USER, 7L);
    when(inboxRepository.findMaxSeqByUserId(7L)).thenReturn(5L);

    messageService.publish(dto, 1L);

    ArgumentCaptor<List<NotifyUserInbox>> captor = captorInboxes();
    verify(inboxRepository).saveAll(captor.capture());
    assertThat(captor.getValue().get(0).getSeq()).isEqualTo(6L);
  }

  @Test
  @DisplayName("多接收方去重：同一用户出现在多个 spec 仅投递一次")
  void should_deduplicate_when_sameUserInMultipleSpecs() {
    PublishDTO dto = new PublishDTO();
    dto.setTitle("hi");
    dto.setContent("body");
    dto.setLevel(MessageLevel.NORMAL);
    PublishDTO.RecipientSpec s1 = new PublishDTO.RecipientSpec();
    s1.setType(RecipientType.USER);
    s1.setId(80L);
    PublishDTO.RecipientSpec s2 = new PublishDTO.RecipientSpec();
    s2.setType(RecipientType.USER);
    s2.setId(80L);
    dto.setRecipients(List.of(s1, s2));
    when(inboxRepository.findMaxSeqByUserId(80L)).thenReturn(0L);

    MessageService.PublishResult result = messageService.publish(dto, 1L);

    assertThat(result.recipientCount()).isEqualTo(1);
  }

  private PublishDTO buildDto(MessageLevel level, RecipientType type, Long recipientId) {
    PublishDTO dto = new PublishDTO();
    dto.setTitle("title");
    dto.setContent("body");
    dto.setLevel(level);
    PublishDTO.RecipientSpec spec = new PublishDTO.RecipientSpec();
    spec.setType(type);
    spec.setId(recipientId);
    dto.setRecipients(List.of(spec));
    return dto;
  }

  @SuppressWarnings("unchecked")
  private ArgumentCaptor<List<NotifyUserInbox>> captorInboxes() {
    return ArgumentCaptor.forClass(List.class);
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T> anyList() {
    return org.mockito.ArgumentMatchers.anyList();
  }
}
