export default {
  title: 'AI Assistant',
  // NOTE: vue-i18n treats `@` as linked-message syntax; escape it with literal interpolation {'@'}.
  placeholder: "e.g. create user alice password Alice{'@'}123",
  send: 'Send',
  callingTool: 'Calling tool',
  thinking: 'Thinking…',
  viewResult: 'View result',
  welcome: "Hi! I'm your system management AI assistant. I can help you with:",
  exampleCreate: "create user alice password Alice{'@'}123",
  exampleDelete: 'delete user 42',
  exampleList: 'list users',
  confirmExecute: 'Confirm',
  confirmCancel: 'Cancel',
  confirmExecuted: '✓ Executed',
  confirmCancelled: '✗ Cancelled',
  // Floating bubble
  bubbleTooltip: 'AI Assistant',
  closeChat: 'Close',
  deleteConfirm: 'Delete this message?',
  deleteFailed: 'Failed to delete message',
  // Capability guide
  capabilities: 'I can help you',
  capUserMgmt: 'User Management',
  capUserMgmtDesc: 'Create, query, delete users; assign roles',
  capRoleMgmt: 'Role Management',
  capRoleMgmtDesc: 'Create roles; assign menu permissions',
  capNavigation: 'Navigation',
  capNavigationDesc: 'Navigate to system management pages',
  // History
  historyLoaded: 'Previous conversation loaded',
  noHistory: 'No conversation yet',
}
