/**
 * 将后端返回的时间字符串按浏览器本地时区格式化展示。
 *
 * 后端 LocalDateTime 序列化后不含时区后缀（如 "2026-07-07T04:30:14"），
 * 但服务器运行在 UTC，因此这里补 'Z' 后再解析，浏览器自动转为本地时区。
 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-'
  try {
    const normalized = /[zZ]$|[+-]\d{2}:?\d{2}$/.test(iso) ? iso : iso + 'Z'
    return new Date(normalized).toLocaleString(navigator.language || 'zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
  } catch {
    return iso
  }
}
