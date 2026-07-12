export default {
  // Table columns
  roleCode: 'Role Code',
  roleName: 'Role Name',
  dataScope: 'Data Scope',
  remark: 'Remark',

  // Data scope options
  scopeAll: 'All Data',
  scopeUnit: 'This Unit',
  scopeUnitBelow: 'This Unit and Below',
  scopeSelf: 'Self Only',
  scopeCustom: 'Custom',

  // Form labels
  customUnit: 'Custom Unit',

  // Modal titles
  addRole: 'Add Role',
  editRole: 'Edit Role',
  assignPermissions: 'Assign Permissions',

  // Placeholders
  roleCodePlaceholder: 'e.g., admin',
  roleNamePlaceholder: 'e.g., Super Admin',
  remarkPlaceholder: 'Remark',

  // Action buttons
  permissions: 'Permissions',
  edit: 'Edit',
  delete: 'Delete',
  savePermissions: 'Save',

  // Status labels
  enabled: 'Enabled',

  // Toast messages
  customScopeFailed: 'Custom data scope save failed: Backend endpoint not yet available (see T24 limitation)',
  assignPermissionsSuccess: 'Permission assignment successful',

  // Validation rules
  roleCodeRequired: 'Role code cannot be empty',
  roleCodeLength: 'Role code length must be between 3-50',
  roleCodePattern: 'Role code can only contain letters, numbers, and underscores',
  roleNameRequired: 'Role name cannot be empty',
  roleNameLength: 'Role name length cannot exceed 100',
  dataScopeRequired: 'Data scope cannot be empty',
  remarkLength: 'Remark length cannot exceed 200',
}