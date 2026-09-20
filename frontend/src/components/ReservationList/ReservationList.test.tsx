import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ReservationList } from './ReservationList'
import { formatDateTime, formatDateTimeRange } from '../../utils/date'
import * as reservationsApi from '../../API/reservations'
import type { Room } from '../../types/room'
import type { Reservation } from '../../types/reservation'

vi.mock('../../API/reservations')

const reservations = vi.mocked(reservationsApi)

function sampleRoom(): Room {
  return {
    id: 'room-1',
    name: 'Room 101',
    building: { id: 'b1', name: 'Main', status: 'ACTIVE' },
    floor: { id: 'f1', buildingId: 'b1', name: '1', status: 'ACTIVE' },
    status: 'ACTIVE',
    version: 0,
    seatingArrangements: [{ id: 'sa-1', name: 'Theater', maxCapacity: 40 }],
    equipmentTypeIds: [],
  }
}

function sampleReservation(overrides: Partial<Reservation> = {}): Reservation {
  return {
    id: 'res-1',
    roomId: 'room-1',
    roomName: 'Room 101',
    startTime: '2026-10-01T09:00:00Z',
    endTime: '2026-10-01T11:00:00Z',
    status: 'RESERVED',
    seatingArrangement: { id: 'sa-1', name: 'Theater', maxCapacity: 40 },
    expectedAttendees: 20,
    additionalEquipment: [{ id: 'eq-1', name: 'Projector', status: 'ACTIVE' }],
    note: 'Initial note',
    createdBy: 'Prof. Smith',
    createdAt: '2026-09-19T10:00:00Z',
    ...overrides,
  }
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe('ReservationList', () => {
  it('renders reservation schedule, status badge, audit info and equipment', async () => {
    const res = sampleReservation()
    reservations.listRoomReservations.mockResolvedValue([res])

    render(<ReservationList room={sampleRoom()} />)

    expect(await screen.findByText(/Prof\. Smith/)).toBeInTheDocument()
    expect(screen.getByText('RESERVED')).toBeInTheDocument()
    expect(screen.getByText(/Theater/)).toBeInTheDocument()
    expect(screen.getByText(/Projector/)).toBeInTheDocument()
    expect(screen.getByText(/Initial note/)).toBeInTheDocument()
    expect(
      screen.getByText(formatDateTimeRange(res.startTime, res.endTime)),
    ).toBeInTheDocument()
    expect(
      screen.getByText(new RegExp(`Created:\\s+${formatDateTime(res.createdAt)}`)),
    ).toBeInTheDocument()
  })

  it('renders empty message when no reservations exist', async () => {
    reservations.listRoomReservations.mockResolvedValue([])

    render(<ReservationList room={sampleRoom()} />)

    expect(await screen.findByText(/no reservations found/i)).toBeInTheDocument()
  })

  it('allows activating a RESERVED booking', async () => {
    const user = userEvent.setup()
    const res = sampleReservation({ status: 'RESERVED' })
    reservations.listRoomReservations.mockResolvedValue([res])
    reservations.activateReservation.mockResolvedValue({ ...res, status: 'ACTIVE' })

    render(<ReservationList room={sampleRoom()} />)

    const activateBtn = await screen.findByRole('button', { name: /activate/i })
    await user.click(activateBtn)

    await waitFor(() => {
      expect(reservations.activateReservation).toHaveBeenCalledWith('res-1')
    })
  })

  it('allows completing an ACTIVE booking', async () => {
    const user = userEvent.setup()
    const res = sampleReservation({ status: 'ACTIVE' })
    reservations.listRoomReservations.mockResolvedValue([res])
    reservations.completeReservation.mockResolvedValue({ ...res, status: 'COMPLETED' })

    render(<ReservationList room={sampleRoom()} />)

    const completeBtn = await screen.findByRole('button', { name: /complete/i })
    await user.click(completeBtn)

    await waitFor(() => {
      expect(reservations.completeReservation).toHaveBeenCalledWith('res-1')
    })
  })

  it('allows expiring a RESERVED booking', async () => {
    const user = userEvent.setup()
    const res = sampleReservation({ status: 'RESERVED' })
    reservations.listRoomReservations.mockResolvedValue([res])
    reservations.expireReservation.mockResolvedValue({ ...res, status: 'EXPIRED' })

    render(<ReservationList room={sampleRoom()} />)

    const expireBtn = await screen.findByRole('button', { name: /expire/i })
    await user.click(expireBtn)

    await waitFor(() => {
      expect(reservations.expireReservation).toHaveBeenCalledWith('res-1')
    })
  })

  it('allows cancelling a RESERVED or ACTIVE booking', async () => {
    const user = userEvent.setup()
    const res = sampleReservation({ status: 'RESERVED' })
    reservations.listRoomReservations.mockResolvedValue([res])
    reservations.cancelReservation.mockResolvedValue({ ...res, status: 'CANCELLED' })

    render(<ReservationList room={sampleRoom()} />)

    const cancelBtn = await screen.findByRole('button', { name: /cancel reservation/i })
    await user.click(cancelBtn)

    await waitFor(() => {
      expect(reservations.cancelReservation).toHaveBeenCalledWith('res-1')
    })
  })

  it('hides operational buttons for terminal states (COMPLETED, EXPIRED, CANCELLED)', async () => {
    reservations.listRoomReservations.mockResolvedValue([
      sampleReservation({ id: 'res-c', status: 'COMPLETED' }),
      sampleReservation({ id: 'res-e', status: 'EXPIRED' }),
      sampleReservation({ id: 'res-x', status: 'CANCELLED' }),
    ])

    render(<ReservationList room={sampleRoom()} />)

    await screen.findByText('COMPLETED')
    expect(screen.queryByRole('button', { name: /activate/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /complete/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /expire/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /cancel reservation/i })).not.toBeInTheDocument()
  })

  it('allows editing metadata on a RESERVED booking', async () => {
    const user = userEvent.setup()
    const res = sampleReservation({ status: 'RESERVED', expectedAttendees: 20, note: 'Initial note' })
    reservations.listRoomReservations.mockResolvedValue([res])
    reservations.updateReservationMetadata.mockResolvedValue({
      ...res,
      expectedAttendees: 30,
      note: 'Updated note',
    })

    render(<ReservationList room={sampleRoom()} />)

    const editBtn = await screen.findByRole('button', { name: /edit/i })
    await user.click(editBtn)

    const attendeesInput = screen.getByLabelText(/edit attendees/i)
    const noteInput = screen.getByLabelText(/edit notes/i)

    await user.clear(attendeesInput)
    await user.type(attendeesInput, '30')
    await user.clear(noteInput)
    await user.type(noteInput, 'Updated note')

    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => {
      expect(reservations.updateReservationMetadata).toHaveBeenCalledWith('res-1', {
        expectedAttendees: 30,
        note: 'Updated note',
      })
    })
  })
})
