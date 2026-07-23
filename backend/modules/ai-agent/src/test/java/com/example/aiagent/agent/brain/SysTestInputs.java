package com.example.aiagent.agent.brain;

import java.util.List;
import java.util.Optional;

/** MockAgentBrainTest 专用工具入参 schema（简化版，仅用于测试）。 */
record SysTestInputs() {
  record EmptyInput() {}

  record IdInput(Long id) {}

  record SearchInput(Optional<String> keyword, Optional<Integer> limit) {}

  record CreateUserInput(
      String username,
      String password,
      Optional<String> realName,
      Optional<String> email,
      Optional<String> phone,
      Optional<Long> unitId) {}

  record CreateRoleInput(
      String roleCode, String roleName, Optional<String> dataScope, Optional<String> remark) {}

  record AssignRolesInput(Long userId, List<Long> roleIds) {}
}
