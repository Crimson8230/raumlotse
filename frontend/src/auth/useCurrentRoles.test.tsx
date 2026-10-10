import { act, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react'
import { Link, MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getCurrentRoles } from '../API/userRoles'
import { useCurrentRoles } from './useCurrentRoles'

vi.mock('../API/userRoles', () => ({ getCurrentRoles: vi.fn() }))

const wrapper = ({ children }: { children: React.ReactNode }) => <MemoryRouter>{children}</MemoryRouter>

describe('useCurrentRoles', () => {
  beforeEach(() => vi.resetAllMocks())

  it('hides previously granted actions while a permission refresh is pending', async () => {
    let finishRefresh: (value: Awaited<ReturnType<typeof getCurrentRoles>>) => void = () => {}
    vi.mocked(getCurrentRoles)
      .mockResolvedValueOnce({ roles: ['STUDENT'], ready: true, adminMode: false,
        permissions: ['READ', 'RESERVE'], canUseAdminMode: false })
      .mockImplementationOnce(() => new Promise(resolve => { finishRefresh = resolve }))
    const { result } = renderHook(() => useCurrentRoles(), { wrapper })
    await waitFor(() => expect(result.current.permissions).toContain('RESERVE'))

    act(() => window.dispatchEvent(new Event('raumlotse:roles-changed')))
    expect(result.current.permissions).toEqual([])
    expect(result.current.loading).toBe(true)

    await act(async () => finishRefresh({ roles: ['VIEWER'], ready: true, adminMode: false,
      permissions: ['READ'], canUseAdminMode: false }))
    await waitFor(() => expect(result.current.permissions).toEqual(['READ']))
  })

  it('refreshes on focus and denies all actions if the lookup fails', async () => {
    vi.mocked(getCurrentRoles).mockResolvedValueOnce({ roles: ['VIEWER'], ready: true,
      adminMode: false, permissions: ['READ'], canUseAdminMode: false })
      .mockRejectedValueOnce(new Error('offline'))
    const { result } = renderHook(() => useCurrentRoles(), { wrapper })
    await waitFor(() => expect(result.current.permissions).toEqual(['READ']))

    act(() => window.dispatchEvent(new Event('focus')))
    expect(result.current.permissions).toEqual([])
    await waitFor(() => expect(result.current.failed).toBe(true))
    expect(result.current.permissions).toEqual([])
    expect(result.current.canUseAdminMode).toBe(false)
  })

  it('reads a new snapshot after navigation', async () => {
    vi.mocked(getCurrentRoles).mockResolvedValueOnce({ roles: ['VIEWER'], ready: true,
      adminMode: false, permissions: ['READ'], canUseAdminMode: false })
      .mockResolvedValueOnce({ roles: ['STUDENT'], ready: true,
        adminMode: false, permissions: ['READ', 'RESERVE'], canUseAdminMode: false })
    function Probe() {
      const current = useCurrentRoles()
      return <><Link to="/next">Weiter</Link><output>{current.permissions.join(',')}</output></>
    }
    render(<MemoryRouter><Probe /></MemoryRouter>)
    await waitFor(() => expect(screen.getByText('READ')).toBeVisible())
    fireEvent.click(screen.getByRole('link', { name: 'Weiter' }))
    await waitFor(() => expect(screen.getByText('READ,RESERVE')).toBeVisible())
    expect(getCurrentRoles).toHaveBeenCalledTimes(2)
  })
})
