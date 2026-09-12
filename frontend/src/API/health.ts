export async function getHealth(): Promise<{ status: string }> {
  const response = await fetch('/api/health')

  if (!response.ok) {
    throw new Error('Health check failed')
  }

  return response.json()
}