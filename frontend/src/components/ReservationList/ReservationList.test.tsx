import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ReservationList } from './ReservationList'
import { formatDateTime, formatDateTimeRange } from '../../utils/date'
import * as reservationsApi from '../../API/reservations'
import type { Room } from '../../types/room'
import type { Reservation } from '../../types/reservation'

const mode = vi.hoisted(() => ({ adminMode: false, permissions: ['READ', 'OWN_RESERVATION_MANAGE'] as string[] }))
vi.mock('../../auth/useAdminMode', () => ({
  useAdminMode: () => ({ loading: false, failed: false, admin: mode.adminMode, adminMode: mode.adminMode,
    permissions: mode.permissions,
    setMode: vi.fn() }),
}))
vi.mock('../../API/reservations')

const reservations = vi.mocked(reservationsApi)

function sampleRoom(): Room {
  return {
    id: 'room-1',
    name: 'Room 101',
    building: { id: 'b1', name: 'Main', status: 'ACTIVE', hasElevator: false },
    floor: { id: 'f1', buildingId: 'b1', name: '1', status: 'ACTIVE', groundFloor: false },
    status: 'ACTIVE',
    version: 0,
    seatingArrangements: [{ id: 'sa-1', name: 'Theater', maxCapacity: 40 }],
    equipmentTypeIds: [],
    notBarrierFree: false,
    barrierFreeReachable: false,
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
    reservedFor: 'Team Alpha',
    createdAt: '2026-09-19T10:00:00Z',
    ownedByMe: true,
    ...overrides,
  }
}

beforeEach(() => {
  vi.resetAllMocks()
  mode.adminMode = false
  mode.permissions = ['READ', 'OWN_RESERVATION_MANAGE']
})

describe('ReservationList', () => {
  it('renders reservation schedule, status badge, audit info and equipment', async () => {
    const res = sampleReservation()
    reservations.listRoomReservations.mockResolvedValue([res])

    render(<ReservationList room={sampleRoom()} />)

    expect(await screen.findByText(/Prof\. Smith/)).toBeInTheDocument()
    expect(screen.getByText(/Team Alpha/)).toBeInTheDocument()
    expect(screen.getByText('Reserviert')).toBeInTheDocument()
    expect(screen.getByText(/Theater/)).toBeInTheDocument()
    expect(screen.getByText(/Projector/)).toBeInTheDocument()
    expect(screen.getByText(/Initial note/)).toBeInTheDocument()
    expect(
      screen.getByText(formatDateTimeRange(res.startTime, res.endTime)),
    ).toBeInTheDocument()
    expect(
      screen.getByText(new RegExp(`Erstellt:\\s+${formatDateTime(res.createdAt ?? '')}`)),
    ).toBeInTheDocument()
  })

  it('renders empty message when no reservations exist', async () => {
    reservations.listRoomReservations.mockResolvedValue([])

    render(<ReservationList room={sampleRoom()} />)

    expect(await screen.findByText(/keine reservierungen/i)).toBeInTheDocument()
  })

  it('allows activating a RESERVED booking', async () => {
    const user = userEvent.setup()
    const res = sampleReservation({ status: 'RESERVED' })
    reservations.listRoomReservations.mockResolvedValue([res])
    reservations.activateReservation.mockResolvedValue({ ...res, status: 'ACTIVE' })

    render(<ReservationList room={sampleRoom()} />)

    const activateBtn = await screen.findByRole('button', { name: /einchecken/i })
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

    const completeBtn = await screen.findByRole('button', { name: /abschließen/i })
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

    const expireBtn = await screen.findByRole('button', { name: /verfallen lassen/i })
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

    const cancelBtn = await screen.findByRole('button', { name: /stornieren/i })
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

    await screen.findByText('Abgeschlossen')
    expect(screen.getByText('Abgelaufen')).toBeInTheDocument()
    expect(screen.getByText('Storniert')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /einchecken/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /abschließen/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /verfallen lassen/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /bearbeiten/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /stornieren/i })).not.toBeInTheDocument()
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

    const editBtn = await screen.findByRole('button', { name: /bearbeiten/i })
    await user.click(editBtn)

    const attendeesInput = screen.getByLabelText(/teilnehmende bearbeiten/i)
    const noteInput = screen.getByLabelText(/notizen bearbeiten/i)
    const reservedForInput = screen.getByLabelText(/reserviert für|edit reserved for/i)

    await user.clear(attendeesInput)
    await user.type(attendeesInput, '30')
    await user.clear(noteInput)
    await user.type(noteInput, 'Updated note')
    await user.clear(reservedForInput)
    await user.type(reservedForInput, 'Neues Team')

    await user.click(screen.getByRole('button', { name: /speichern/i }))

    await waitFor(() => {
      expect(reservations.updateReservationMetadata).toHaveBeenCalledWith('res-1', {
        expectedAttendees: 30,
        note: 'Updated note',
        reservedFor: 'Neues Team',
      })
    })
  })

  it('validates that reservedFor is mandatory and non-blank when editing', async () => {
    const user = userEvent.setup()
    const res = sampleReservation({ status: 'RESERVED', reservedFor: 'Original Team' })
    reservations.listRoomReservations.mockResolvedValue([res])

    render(<ReservationList room={sampleRoom()} />)

    const editBtn = await screen.findByRole('button', { name: /bearbeiten/i })
    await user.click(editBtn)

    const reservedForInput = screen.getByLabelText(/reserviert für|edit reserved for/i)
    await user.clear(reservedForInput)

    await user.click(screen.getByRole('button', { name: /speichern/i }))

    expect(await screen.findByText(/reserviert für ist ein pflichtfeld|reserved for is required/i)).toBeInTheDocument()
    expect(reservations.updateReservationMetadata).not.toHaveBeenCalled()
  })

  it('rejects fractional attendee counts in the edit form', async () => {
    const user = userEvent.setup()
    reservations.listRoomReservations.mockResolvedValue([sampleReservation()])
    render(<ReservationList room={sampleRoom()} />)

    await user.click(await screen.findByRole('button', { name: /bearbeiten/i }))
    const attendeesInput = screen.getByLabelText(/teilnehmende bearbeiten/i)
    await user.clear(attendeesInput)
    await user.type(attendeesInput, '1.5')
    await user.click(screen.getByRole('button', { name: /speichern/i }))

    expect(await screen.findByText(/ganze zahl/i)).toBeInTheDocument()
    expect(reservations.updateReservationMetadata).not.toHaveBeenCalled()
  })

  describe('ownership (feature 013)', () => {
    function redacted(): Reservation {
      return sampleReservation({
        id: 'res-other',
        seatingArrangement: null,
        expectedAttendees: null,
        additionalEquipment: [],
        note: null,
        createdBy: null,
        reservedFor: null,
        createdAt: null,
        ownedByMe: false,
      })
    }

    it('shows other users\' bookings as occupied without personal details or actions', async () => {
      reservations.listRoomReservations.mockResolvedValue([redacted()])

      render(<ReservationList room={sampleRoom()} />)

      expect(await screen.findByText('Belegt')).toBeInTheDocument()
      expect(screen.queryByText(/Booked by/)).not.toBeInTheDocument()
      expect(screen.queryByText(/Reserviert für/)).not.toBeInTheDocument()
      expect(screen.queryByRole('button')).not.toBeInTheDocument()
    })

    it('offers no actions for a visible booking owned by somebody else outside administration mode', async () => {
      reservations.listRoomReservations.mockResolvedValue([sampleReservation({ ownedByMe: false })])

      render(<ReservationList room={sampleRoom()} />)

      await screen.findByText(/Prof\. Smith/)
      expect(screen.queryByRole('button', { name: 'Stornieren' })).not.toBeInTheDocument()
    })

    it('offers the owner the actions in the user view', async () => {
      reservations.listRoomReservations.mockResolvedValue([sampleReservation({ ownedByMe: true })])

      render(<ReservationList room={sampleRoom()} />)

      expect(await screen.findByRole('button', { name: 'Stornieren' })).toBeInTheDocument()
    })

    it('lets administrators in administration mode manage other users\' bookings', async () => {
      mode.adminMode = true
      mode.permissions = ['READ', 'OWN_RESERVATION_MANAGE', 'OTHER_RESERVATION_MANAGE']
      reservations.listRoomReservations.mockResolvedValue([sampleReservation({ ownedByMe: false })])

      render(<ReservationList room={sampleRoom()} />)

      expect(await screen.findByRole('button', { name: 'Stornieren' })).toBeInTheDocument()
    })

    it('shows an earlier own booking to a READ-only viewer without management actions', async () => {
      mode.permissions = ['READ']
      reservations.listRoomReservations.mockResolvedValue([sampleReservation({ ownedByMe: true })])
      render(<ReservationList room={sampleRoom()} />)
      expect(await screen.findByText(/Team Alpha/)).toBeVisible()
      expect(screen.queryByRole('button', { name: 'Bearbeiten' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Stornieren' })).not.toBeInTheDocument()
    })

    it('fetches foreign details only when OTHER_RESERVATION_MANAGE is granted', async () => {
      mode.permissions = ['READ', 'OTHER_RESERVATION_MANAGE']
      reservations.listRoomReservations.mockResolvedValue([redacted()])
      reservations.getReservation.mockResolvedValue(sampleReservation({ id: 'res-other', ownedByMe: false }))
      render(<ReservationList room={sampleRoom()} />)
      await userEvent.click(await screen.findByRole('button', { name: 'Details anzeigen' }))
      expect(await screen.findByText(/Team Alpha/)).toBeVisible()
      expect(screen.getByRole('button', { name: 'Stornieren' })).toBeVisible()
      expect(reservations.getReservation).toHaveBeenCalledWith('res-other')
    })
  })
})
