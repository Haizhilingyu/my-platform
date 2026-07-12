export default {
  // Table columns
  roleCode: '角色编码',
  roleName: '角色名称',
  dataScope: '数据范围',
  remark: '备注',

  // Data scope options
  scopeAll: '全部数据',
  scopeUnit: '本单位',
  scopeUnitBelow: '本单位及下属',
  scopeSelf: '仅本人',
  scopeCustom: '自定义',

  // Form labels
  customUnit: '自定义单位',

  // Modal titles
  addRole: '新增角色',
  editRole: '编辑角色',
  assignPermissions: '分配权限',

  // Placeholders
  roleCodePlaceholder: '如 admin',
  roleNamePlaceholder: '如 超级管理员',
  remarkPlaceholder: '备注',

  // Action buttons
  permissions: '权限',
  edit: '编辑',
  delete: '删除',
  savePermissions: '保存',

  // Status labels
  enabled: '启用',

  // Toast messages
  customScopeFailed: '自定义数据范围保存失败：后端暂未提供该端点（见 T24 limitation）',
  assignPermissionsSuccess: '权限分配成功',

  // Validation rules
  roleCodeRequired: '角色编码不能为空',
  roleCodeLength: '角色编码长度需在3-50之间',
  roleCodePattern: '角色编码只能包含字母、数字、下划线',
  roleNameRequired: '角色名称不能为空',
  roleNameLength: '角色名称长度不能超过100',
  dataScopeRequired: '数据范围不能为空',
  remarkLength: '备注长度不能超过200',
}