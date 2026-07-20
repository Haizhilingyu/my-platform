export default {
  title: 'AI Assistant',
  // NOTE: vue-i18n treats `@` as linked-message syntax; escape it with literal interpolation {'@'}.
  placeholder: "e.g. create user alice password Alice{'@'}123",
  send: 'Send',
  callingTool: 'Calling tool',
  thinking: 'Working…',
  viewResult: 'View result',
  welcome: "I'm your AI assistant. I can manage the system within your permissions. Try:",
  exampleCreate: "create user alice password Alice{'@'}123",
  exampleDelete: 'delete user 42',
  confirmExecute: 'Execute',
  confirmCancel: 'Cancel',
  confirmExecuted: 'Executed',
  confirmCancelled: 'Cancelled',
}
