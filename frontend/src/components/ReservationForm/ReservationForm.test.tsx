import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { ReservationForm } from './ReservationForm'
import { ApiError } from '../../API/client'
import * as reservationsApi from '../../API/reservations'
import type { Room } from '../../types/room'
import type { Reservation } from '../../types/reservation'

vi.mock('../../API/reservations')

const reservations = vi.mocked(reservationsApi)

function sampleRoom(overrides: Partial<Room> = {}): Room {
  return {
    id: 'room-1',
    name: 'Room 101',
    building: { id: 'b1', name: 'Main Building', status: 'ACTIVE' },
    floor: { id: 'f1', buildingId: 'b1', name: '1st Floor', status: 'ACTIVE' },
    status: 'ACTIVE',
    version: 0,
    seatingArrangements: [
      { id: 'seat-1', name: 'Theater', maxCapacity: 40 },
      { id: 'seat-2', name: 'Classroom', maxCapacity: 20 },
    ],
    equipmentTypeIds: [],
    ...overrides,
  }
}

function sampleReservation(): Reservation {
  return {
    id: 'res-1',
    roomId: 'room-1',
    roomName: 'Room 101',
    startTime: '2026-10-01T10:00:00Z',
    endTime: '2026-10-01T11:30:00Z',
    status: 'RESERVED',
    seatingArrangement: { id: 'seat-1', name: 'Theater', maxCapacity: 40 },
    expectedAttendees: 30,
    additionalEquipment: [],
    note: 'Department Sync',
    createdBy: 'Jane Doe',
    createdAt: '2026-09-19T10:00:00Z',
  }
}

beforeEach(() => {
  vi.resetAllMocks()
  reservations.getAvailableEquipment.mockResolvedValue([])
})

describe('ReservationForm', () => {
  it('renders form inputs and auto-selects layout when room has only one arrangement', async () => {
    const singleLayoutRoom = sampleRoom({
      seatingArrangements: [{ id: 'seat-1', name: 'Theater', maxCapacity: 40 }],
    })

    render(<ReservationForm room={singleLayoutRoom} onSaved={vi.fn()} />)

    expect(screen.getByLabelText(/start time/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/end time/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/attendees/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/booked by/i)).toBeInTheDocument()

    const select = screen.getByLabelText(/seating arrangement/i) as HTMLSelectElement
    expect(select.value).toBe('seat-1')
  })

  it('validates attendee count against chosen seating arrangement capacity', async () => {
    const user = userEvent.setup()
    render(<ReservationForm room={sampleRoom()} onSaved={vi.fn()} />)

    const attendeesInput = screen.getByLabelText(/attendees/i)
    const select = screen.getByLabelText(/seating arrangement/i)

    await user.selectOptions(select, 'seat-2') // maxCapacity: 20
    await user.type(attendeesInput, '25')

    const submitBtn = screen.getByRole('button', { name: /confirm reservation/i })
    await user.click(submitBtn)

    expect(await screen.findByText(/cannot exceed arrangement capacity \(20\)/i)).toBeInTheDocument()
    expect(reservations.createReservation).not.toHaveBeenCalled()
  })

  it('submits valid reservation and triggers onSaved', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()
    const res = sampleReservation()
    reservations.createReservation.mockResolvedValue(res)

    render(<ReservationForm room={sampleRoom()} onSaved={onSaved} />)

    await user.type(screen.getByLabelText(/start time/i), '2026-10-01T10:00')
    await user.type(screen.getByLabelText(/end time/i), '2026-10-01T11:30')
    await user.selectOptions(screen.getByLabelText(/seating arrangement/i), 'seat-1')
    await user.type(screen.getByLabelText(/attendees/i), '30')
    await user.type(screen.getByLabelText(/booked by/i), 'Jane Doe')
    await user.type(screen.getByLabelText(/notes/i), 'Department Sync')

    await user.click(screen.getByRole('button', { name: /confirm reservation/i }))

    await waitFor(() => {
      expect(reservations.createReservation).toHaveBeenCalledWith('room-1', expect.objectContaining({
        seatingArrangementId: 'seat-1',
        expectedAttendees: 30,
        createdBy: 'Jane Doe',
        note: 'Department Sync',
      }))
      expect(onSaved).toHaveBeenCalledWith(res)
    })
  })

  it('renders available equipment checkboxes and passes selected equipment on submit', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()
    reservations.getAvailableEquipment.mockResolvedValue([
      { id: 'eq-1', name: 'Microphone', status: 'ACTIVE' },
      { id: 'eq-2', name: 'Projector', status: 'ACTIVE' },
    ])
    reservations.createReservation.mockResolvedValue(sampleReservation())

    render(<ReservationForm room={sampleRoom()} onSaved={onSaved} />)

    expect(await screen.findByLabelText('Microphone')).toBeInTheDocument()
    expect(screen.getByLabelText('Projector')).toBeInTheDocument()

    await user.click(screen.getByLabelText('Microphone'))

    await user.type(screen.getByLabelText(/start time/i), '2026-10-01T10:00')
    await user.type(screen.getByLabelText(/end time/i), '2026-10-01T11:30')
    await user.selectOptions(screen.getByLabelText(/seating arrangement/i), 'seat-1')
    await user.type(screen.getByLabelText(/attendees/i), '30')
    await user.type(screen.getByLabelText(/booked by/i), 'Jane Doe')

    await user.click(screen.getByRole('button', { name: /confirm reservation/i }))

    await waitFor(() => {
      expect(reservations.createReservation).toHaveBeenCalledWith('room-1', expect.objectContaining({
        additionalEquipmentTypeIds: ['eq-1'],
      }))
    })
  })

  it('displays empty notice when no additional equipment is available', async () => {
    reservations.getAvailableEquipment.mockResolvedValue([])

    render(<ReservationForm room={sampleRoom()} onSaved={vi.fn()} />)

    expect(
      await screen.findByText(/all catalog equipment is already present in this room/i),
    ).toBeInTheDocument()
  })

  it('displays conflict message and link to /rooms when booking conflicts with an existing reservation', async () => {
    const user = userEvent.setup()
    reservations.createReservation.mockRejectedValue(
      new ApiError(409, {
        status: 409,
        title: 'Conflict',
        detail: 'Scheduling conflict: The room is already reserved during this time.',
      }),
    )

    render(
      <MemoryRouter>
        <ReservationForm room={sampleRoom()} onSaved={vi.fn()} />
      </MemoryRouter>,
    )

    await user.type(screen.getByLabelText(/start time/i), '2026-10-01T10:00')
    await user.type(screen.getByLabelText(/end time/i), '2026-10-01T11:30')
    await user.selectOptions(screen.getByLabelText(/seating arrangement/i), 'seat-1')
    await user.type(screen.getByLabelText(/attendees/i), '30')
    await user.type(screen.getByLabelText(/booked by/i), 'Jane Doe')

    await user.click(screen.getByRole('button', { name: /confirm reservation/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveClass('feedback-conflict')
    expect(alert).toHaveTextContent(/scheduling conflict/i)

    const link = screen.getByRole('link', { name: /zurück zur raumübersicht/i })
    expect(link).toBeInTheDocument()
    expect(link).toHaveAttribute('href', '/rooms')
  })
})
