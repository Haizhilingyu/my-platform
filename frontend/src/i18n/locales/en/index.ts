import common from './common'
import validation from './validation'
import route from './route'
import login from './login'
import layout from './layout'
import dashboard from './dashboard'
import notFound from './notFound'
import sysUser from './sys/user'
import sysRole from './sys/role'
import sysMenu from './sys/menu'
import sysUnit from './sys/unit'
import sysConfig from './sys/config'
import sysMessage from './sys/message'
import sysSession from './sys/session'
import sysAudit from './sys/audit'
import sysApp from './sys/app'

export default {
  common,
  validation,
  route,
  login,
  layout,
  dashboard,
  notFound,
  sys: {
    user: sysUser,
    role: sysRole,
    menu: sysMenu,
    unit: sysUnit,
    config: sysConfig,
    message: sysMessage,
    session: sysSession,
    audit: sysAudit,
    app: sysApp,
  },
}
