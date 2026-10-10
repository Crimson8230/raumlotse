import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CheckInQrCode } from './CheckInQrCode'

describe('CheckInQrCode', () => {
  it('renders an accessible SVG QR code for the link', async () => {
    render(<CheckInQrCode link="https://raumlotse.example/rooms/r1/check-in?method=qr" roomName="Seminarraum 1" />)
    const image = await screen.findByRole('img', { name: 'QR-Code für den Check-in in Seminarraum 1' })
    expect(image.querySelector('svg')).not.toBeNull()
  })
})
