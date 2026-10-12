/** Backend reporting days are UTC ISO calendar dates. Weeks start on Monday UTC. */
export const utcWeekStart = (day: string): string => {
  const date = new Date(`${day}T00:00:00.000Z`)
  if (Number.isNaN(date.getTime())) throw new Error('Invalid UTC reporting day')
  const daysSinceMonday = (date.getUTCDay() + 6) % 7
  date.setUTCDate(date.getUTCDate() - daysSinceMonday)
  return date.toISOString().slice(0, 10)
}
