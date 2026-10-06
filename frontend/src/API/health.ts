export async function getHealth(): Promise<{ status: string }> {
  const response = await fetch('/api/health')

  if (!response.ok) {
    throw new Error('Statusabfrage fehlgeschlagen')
  }

  return response.json()
}