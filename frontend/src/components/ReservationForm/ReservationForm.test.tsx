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

let mockAuthState = {
  state: 'authenticated',
  user: { userId: 'user-1', displayName: 'Jane Doe' } as { userId: string; displayName: string } | null,
}

vi.mock('../../auth/useAuth', () => ({
  useAuth: () => mockAuthState,
}))

const reservations = vi.mocked(reservationsApi)
const SEAT_ONE_ID = '00000000-0000-4000-8000-000000000001'
const SEAT_TWO_ID = '00000000-0000-4000-8000-000000000002'
const EQUIPMENT_ONE_ID = '00000000-0000-4000-8000-000000000003'
const EQUIPMENT_TWO_ID = '00000000-0000-4000-8000-000000000004'

function sampleRoom(overrides: Partial<Room> = {}): Room {
  return {
    id: 'room-1',
    name: 'Room 101',
    building: { id: 'b1', name: 'Main Building', status: 'ACTIVE', hasElevator: false },
    floor: { id: 'f1', buildingId: 'b1', name: '1st Floor', status: 'ACTIVE', groundFloor: false },
    status: 'ACTIVE',
    version: 0,
    seatingArrangements: [
      { id: SEAT_ONE_ID, name: 'Theater', maxCapacity: 40 },
      { id: SEAT_TWO_ID, name: 'Classroom', maxCapacity: 20 },
    ],
    equipmentTypeIds: [],
    notBarrierFree: false,
    barrierFreeReachable: false,
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
    seatingArrangement: { id: SEAT_ONE_ID, name: 'Theater', maxCapacity: 40 },
    expectedAttendees: 30,
    additionalEquipment: [],
    note: 'Department Sync',
    createdBy: 'Jane Doe',
    reservedFor: 'Jane Doe',
    createdAt: '2026-09-19T10:00:00Z',
  }
}

beforeEach(() => {
  vi.resetAllMocks()
  reservations.getAvailableEquipment.mockResolvedValue([])
  mockAuthState = {
    state: 'authenticated',
    user: { userId: 'user-1', displayName: 'Jane Doe' },
  }
})

describe('ReservationForm', () => {
  it('pre-fills reservedFor input and shows the authenticated creator as read-only', async () => {
    mockAuthState = {
      state: 'authenticated',
      user: { userId: 'user-1', displayName: 'Max Mustermann' },
    }
    render(<ReservationForm room={sampleRoom()} onSaved={vi.fn()} />)
    const reservedForInput = screen.getByLabelText(/reserviert für|reserved for/i) as HTMLInputElement
    expect(reservedForInput).toBeInTheDocument()
    expect(reservedForInput.value).toBe('Max Mustermann')
    const bookedByInput = screen.getByLabelText(/booked by/i) as HTMLInputElement
    expect(bookedByInput).toHaveValue('Max Mustermann')
    expect(bookedByInput).toHaveAttribute('readonly')
  })

  it('allows overwriting reservedFor with custom text and submits it', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()
    mockAuthState = {
      state: 'authenticated',
      user: { userId: 'user-1', displayName: 'Max Mustermann' },
    }
    reservations.createReservation.mockResolvedValue(sampleReservation())

    render(<ReservationForm room={sampleRoom()} onSaved={onSaved} />)

    await user.type(screen.getByLabelText(/start time/i), '2026-10-01T10:00')
    await user.type(screen.getByLabelText(/end time/i), '2026-10-01T11:30')
    await user.selectOptions(screen.getByLabelText(/seating arrangement/i), SEAT_ONE_ID)
    await user.type(screen.getByLabelText(/attendees/i), '30')

    const reservedForInput = screen.getByLabelText(/reserviert für|reserved for/i)
    await user.clear(reservedForInput)
    await user.type(reservedForInput, 'Projektgruppe Web')

    await user.click(screen.getByRole('button', { name: /confirm reservation/i }))

    await waitFor(() => {
      expect(reservations.createReservation).toHaveBeenCalledWith('room-1', expect.objectContaining({
        seatingArrangementId: SEAT_ONE_ID,
        expectedAttendees: 30,
        reservedFor: 'Projektgruppe Web',
      }))
    })
  })

  it('validates that reservedFor is mandatory and non-blank', async () => {
    const user = userEvent.setup()
    mockAuthState = {
      state: 'authenticated',
      user: { userId: 'user-1', displayName: 'Max Mustermann' },
    }

    render(<ReservationForm room={sampleRoom()} onSaved={vi.fn()} />)

    const reservedForInput = screen.getByLabelText(/reserviert für|reserved for/i)
    await user.clear(reservedForInput)

    await user.type(screen.getByLabelText(/start time/i), '2026-10-01T10:00')
    await user.type(screen.getByLabelText(/end time/i), '2026-10-01T11:30')
    await user.selectOptions(screen.getByLabelText(/seating arrangement/i), SEAT_ONE_ID)
    await user.type(screen.getByLabelText(/attendees/i), '30')

    await user.click(screen.getByRole('button', { name: /confirm reservation/i }))

    expect(await screen.findByText(/reserviert für ist ein pflichtfeld|reserved for is required/i)).toBeInTheDocument()
    expect(reservations.createReservation).not.toHaveBeenCalled()
  })

  it('displays an authentication prompt when visitor is unauthenticated', async () => {
    mockAuthState = {
      state: 'anonymous',
      user: null,
    }

    render(
      <MemoryRouter>
        <ReservationForm room={sampleRoom()} onSaved={vi.fn()} />
      </MemoryRouter>,
    )

    expect(screen.getByText(/bitte melden sie sich an|sign in to make a reservation/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /confirm reservation/i })).not.toBeInTheDocument()
  })

  it('renders form inputs and auto-selects layout when room has only one arrangement', async () => {
    const singleLayoutRoom = sampleRoom({
      seatingArrangements: [{ id: SEAT_ONE_ID, name: 'Theater', maxCapacity: 40 }],
    })

    render(<ReservationForm room={singleLayoutRoom} onSaved={vi.fn()} />)

    expect(screen.getByLabelText(/start time/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/end time/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/attendees/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/reserviert für|reserved for/i)).toBeInTheDocument()

    const select = screen.getByLabelText(/seating arrangement/i) as HTMLSelectElement
    expect(select.value).toBe(SEAT_ONE_ID)
  })

  it('validates attendee count against chosen seating arrangement capacity', async () => {
    const user = userEvent.setup()
    render(<ReservationForm room={sampleRoom()} onSaved={vi.fn()} />)

    const attendeesInput = screen.getByLabelText(/attendees/i)
    const select = screen.getByLabelText(/seating arrangement/i)

    await user.selectOptions(select, SEAT_TWO_ID) // maxCapacity: 20
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
    await user.selectOptions(screen.getByLabelText(/seating arrangement/i), SEAT_ONE_ID)
    await user.type(screen.getByLabelText(/attendees/i), '30')
    const reservedForInput = screen.getByLabelText(/reserviert für|reserved for/i)
    await user.clear(reservedForInput)
    await user.type(reservedForInput, 'Jane Doe')
    await user.type(screen.getByLabelText(/notes/i), 'Department Sync')

    await user.click(screen.getByRole('button', { name: /confirm reservation/i }))

    await waitFor(() => {
      expect(reservations.createReservation).toHaveBeenCalledWith('room-1', expect.objectContaining({
        seatingArrangementId: SEAT_ONE_ID,
        expectedAttendees: 30,
        reservedFor: 'Jane Doe',
        note: 'Department Sync',
      }))
      expect(onSaved).toHaveBeenCalledWith(res)
    })
  })

  it('renders available equipment checkboxes and passes selected equipment on submit', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()
    reservations.getAvailableEquipment.mockResolvedValue([
      { id: EQUIPMENT_ONE_ID, name: 'Microphone', status: 'ACTIVE' },
      { id: EQUIPMENT_TWO_ID, name: 'Projector', status: 'ACTIVE' },
    ])
    reservations.createReservation.mockResolvedValue(sampleReservation())

    render(<ReservationForm room={sampleRoom()} onSaved={onSaved} />)

    expect(await screen.findByLabelText('Microphone')).toBeInTheDocument()
    expect(screen.getByLabelText('Projector')).toBeInTheDocument()

    await user.click(screen.getByLabelText('Microphone'))

    await user.type(screen.getByLabelText(/start time/i), '2026-10-01T10:00')
    await user.type(screen.getByLabelText(/end time/i), '2026-10-01T11:30')
    await user.selectOptions(screen.getByLabelText(/seating arrangement/i), SEAT_ONE_ID)
    await user.type(screen.getByLabelText(/attendees/i), '30')

    await user.click(screen.getByRole('button', { name: /confirm reservation/i }))

    await waitFor(() => {
      expect(reservations.createReservation).toHaveBeenCalledWith('room-1', expect.objectContaining({
        additionalEquipmentTypeIds: [EQUIPMENT_ONE_ID],
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
    await user.selectOptions(screen.getByLabelText(/seating arrangement/i), SEAT_ONE_ID)
    await user.type(screen.getByLabelText(/attendees/i), '30')

    await user.click(screen.getByRole('button', { name: /confirm reservation/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveClass('feedback-conflict')
    expect(alert).toHaveTextContent(/scheduling conflict/i)

    const link = screen.getByRole('link', { name: /zurück zur raumübersicht/i })
    expect(link).toBeInTheDocument()
    expect(link).toHaveAttribute('href', '/rooms')
  })

  it('pre-fills start and end from ISO props as local datetime values that stay editable', async () => {
    const user = userEvent.setup()
    const start = new Date(2026, 9, 5, 11, 0)
    const end = new Date(2026, 9, 5, 12, 30)

    render(
      <ReservationForm
        room={sampleRoom()}
        onSaved={vi.fn()}
        initialStartTime={start.toISOString()}
        initialEndTime={end.toISOString()}
      />,
    )

    const startInput = screen.getByLabelText(/start time/i)
    expect(startInput).toHaveValue('2026-10-05T11:00')
    expect(screen.getByLabelText(/end time/i)).toHaveValue('2026-10-05T12:30')

    await user.clear(startInput)
    await user.type(startInput, '2026-10-05T11:15')
    expect(startInput).toHaveValue('2026-10-05T11:15')
  })

  it('leaves start and end empty without pre-fill props', () => {
    render(<ReservationForm room={sampleRoom()} onSaved={vi.fn()} />)

    expect(screen.getByLabelText(/start time/i)).toHaveValue('')
    expect(screen.getByLabelText(/end time/i)).toHaveValue('')
  })

  it('starts Email Notification unchecked and submits its final visible value', async () => {
    const user = userEvent.setup()
    reservations.createReservation.mockResolvedValue(sampleReservation())
    const { unmount } = render(<ReservationForm room={sampleRoom()} onSaved={vi.fn()} />)
    const checkbox = screen.getByRole('checkbox', { name: 'Email Notification' })
    const submit = screen.getByRole('button', { name: /confirm reservation/i })
    expect(checkbox).not.toBeChecked()
    expect(checkbox.compareDocumentPosition(submit) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()

    await user.click(checkbox)
    await user.type(screen.getByLabelText(/start time/i), '2026-10-01T10:00')
    await user.type(screen.getByLabelText(/end time/i), '2026-10-01T11:30')
    await user.selectOptions(screen.getByLabelText(/seating arrangement/i), SEAT_ONE_ID)
    await user.type(screen.getByLabelText(/attendees/i), '4')
    await user.click(submit)

    await waitFor(() => expect(reservations.createReservation).toHaveBeenCalledWith(
      'room-1', expect.objectContaining({ emailNotification: true }),
    ))

    unmount()
    render(<ReservationForm room={sampleRoom()} onSaved={vi.fn()} />)
    expect(screen.getByRole('checkbox', { name: 'Email Notification' })).not.toBeChecked()
  })
})
