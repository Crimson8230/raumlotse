import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import HomePage from './HomePage'
import * as healthApi from '../API/health'

vi.mock('../API/health')

const health = vi.mocked(healthApi)

describe('HomePage', () => {
  it('shows the pending health check with the loading status class', () => {
    health.getHealth.mockReturnValue(new Promise(() => {}))

    render(<HomePage />)

    expect(screen.getByText('checking...')).toHaveClass('status-loading')
  })

  it('shows an unreachable backend with the semantic error status class', async () => {
    health.getHealth.mockRejectedValue(new Error('network error'))

    render(<HomePage />)

    expect(await screen.findByText('unreachable')).toHaveClass('status-unreachable')
  })
})
