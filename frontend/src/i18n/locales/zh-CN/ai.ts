export default {
  title: 'AI 助手',
  // 注意：vue-i18n 把 `@` 当作 linked message 语法，必须用字面插值 {'@'} 转义，否则编译报 "Invalid linked format"。
  placeholder: "输入消息，例如：创建用户 alice 密码 Alice{'@'}123",
  send: '发送',
  callingTool: '调用工具',
  thinking: '正在思考…',
  viewResult: '查看结果',
  welcome: '你好！我是系统管理智能助手，可以帮你操作以下功能：',
  exampleCreate: "创建用户 alice 密码 Alice{'@'}123",
  exampleDelete: '删除用户 42',
  exampleList: '查询用户列表',
  confirmExecute: '确认执行',
  confirmCancel: '取消',
  confirmExecuted: '✓ 已执行',
  confirmCancelled: '✗ 已取消',
  // 悬浮气泡
  bubbleTooltip: 'AI 助手',
  closeChat: '关闭',
  clearHistory: '清空对话',
  clearHistoryConfirm: '确定清空所有对话记录？',
  // 能力引导
  capabilities: '我能帮你做',
  capUserMgmt: '用户管理',
  capUserMgmtDesc: '创建、查询、删除用户，分配角色',
  capRoleMgmt: '角色管理',
  capRoleMgmtDesc: '创建角色，分配菜单权限',
  capNavigation: '页面导航',
  capNavigationDesc: '跳转到系统管理页面',
  // 历史记录
  historyLoaded: '已加载历史对话',
  noHistory: '暂无对话记录',
}
