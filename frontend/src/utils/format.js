/** 页面里反复要用的几个小格式化函数 */

/** 2026-09-22T14:30:00 -> 09-22 14:30 */
export function formatTime(value) {
  if (!value) {
    return '—'
  }
  const text = String(value).replace('T', ' ')
  return text.length >= 16 ? text.slice(5, 16) : text
}

/** 2026-09-22T14:30:00 -> 2026-09-22 14:30 */
export function formatFull(value) {
  if (!value) {
    return '—'
  }
  return String(value).replace('T', ' ').slice(0, 16)
}

export function formatDate(value) {
  if (!value) {
    return '—'
  }
  return String(value).slice(0, 10)
}

/** 金额：保留两位，前端只负责显示 */
export function money(value) {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  return Number(value).toFixed(2)
}

/** 从开局时间算到「现在」已经过了多少分钟（向上取整，至少 1 分钟） */
export function elapsedMinutes(startTime, now) {
  if (!startTime) {
    return 0
  }
  const start = new Date(String(startTime).replace(' ', 'T')).getTime()
  if (Number.isNaN(start)) {
    return 0
  }
  const diff = Math.floor((now - start) / 60000)
  return diff < 1 ? 1 : diff
}

/** 不足一小时按一小时 —— 和后端 BilliardDict.MIN_BILLABLE_MINUTES 一个口径，只用于前端预览 */
export function previewBillable(elapsed) {
  return Math.max(elapsed, 60)
}
