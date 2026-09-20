import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { UserRoleEditor } from './UserRoleEditor'
describe('role editor', () => {
  it('shows five labeled roles, reports empty selection and supports cancel', async () => {
    const cancel = vi.fn()
    render(<UserRoleEditor value={[]} onChange={vi.fn()} onSave={vi.fn()} onCancel={cancel} busy={false} disabled={false} />)
    expect(screen.getAllByRole('checkbox')).toHaveLength(5)
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled()
    expect(screen.getByText('Mindestens eine Rolle ist erforderlich.')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    expect(cancel).toHaveBeenCalledOnce()
  })
  it('preserves Viewer with another role and disables submission during save', async () => {
    const change = vi.fn()
    const { rerender } = render(<UserRoleEditor value={['VIEWER']} onChange={change} onSave={vi.fn()} onCancel={vi.fn()} busy={false} disabled={false} />)
    await userEvent.click(screen.getByRole('checkbox', { name: 'Student' }))
    expect(change).toHaveBeenCalledWith(['VIEWER', 'STUDENT'])
    rerender(<UserRoleEditor value={['VIEWER', 'STUDENT']} onChange={change} onSave={vi.fn()} onCancel={vi.fn()} busy disabled={false} />)
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled()
  })
})

