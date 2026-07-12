import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    titleKey?: string
    permission?: string
  }
}
