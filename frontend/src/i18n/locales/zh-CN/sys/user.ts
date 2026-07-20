export default {
  // Table columns
  username: '用户名',
  realName: '姓名',
  email: '邮箱',
  phone: '电话',
  unit: '单位',
  role: '角色',
  createTime: '创建时间',

  // Status labels
  locked: '锁定',
  normal: '正常',
  disabled: '禁用',

  // Form labels
  password: '密码',
  confirmPassword: '确认密码',

  // Placeholders
  searchPlaceholder: '搜索用户名/姓名/电话',
  usernamePlaceholder: '请输入用户名',
  passwordPlaceholder: '请输入密码',
  realNamePlaceholder: '请输入姓名',
  emailPlaceholder: '请输入邮箱',
  phonePlaceholder: '请输入电话',
  unitPlaceholder: '请选择单位',
  rolePlaceholder: '请选择角色',

  // Modal titles
  addUserTitle: '新增用户',
  editUserTitle: '编辑用户',

  // Action buttons
  edit: '编辑',
  unlock: '解锁',
  resetPassword: '重置密码',

  // Popconfirm
  confirmResetPassword: "确认重置密码为 User{'@'}123456？",

  // Toast messages
  unlockSuccess: '解锁成功',
  unlockFailed: '解锁失败',
  passwordReset: "密码已重置为 User{'@'}123456",
  resetFailed: '重置失败',

  // Validation rules
  usernameRequired: '用户名不能为空',
  usernameLength: '用户名长度需在3-32之间',
  usernamePattern: '用户名只能包含字母、数字、下划线',
  passwordRequired: '密码不能为空',
  passwordLength: '密码长度需在6-32之间',
  realNameLength: '姓名长度不能超过50',
  phoneLength: '手机号长度不能超过20',
  phonePattern: '手机号格式不正确',
}