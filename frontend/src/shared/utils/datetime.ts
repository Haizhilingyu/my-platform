/**
 * 将后端返回的时间字符串按浏览器本地时区格式化为 yyyy-MM-dd HH:mm:ss。
 *
 * 后端 LocalDateTime 序列化后不含时区后缀（如 "2026-07-07T04:30:14"），
 * 但服务器运行在 UTC，因此这里补 'Z' 后再解析，浏览器自动转为本地时区。
 * 使用 Date 各分量方法（getFullYear/getHours 等）确保输出始终为
 * yyyy-MM-dd HH:mm:ss 格式，不受浏览器 locale 影响。
 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-'
  try {
    const normalized = /[zZ]$|[+-]\d{2}:?\d{2}$/.test(iso) ? iso : iso + 'Z'
    const d = new Date(normalized)
    const yyyy = d.getFullYear()
    const MM = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    const HH = String(d.getHours()).padStart(2, '0')
    const mm = String(d.getMinutes()).padStart(2, '0')
    const ss = String(d.getSeconds()).padStart(2, '0')
    return `${yyyy}-${MM}-${dd} ${HH}:${mm}:${ss}`
  } catch {
    return iso
  }
}
