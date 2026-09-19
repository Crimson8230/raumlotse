import { apiRequest } from './client'
import type {
  EquipmentTypeSummary,
  Reservation,
  ReservationCreatePayload,
  ReservationUpdatePayload,
} from '../types/reservation'

export function getAvailableEquipment(roomId: string): Promise<EquipmentTypeSummary[]> {
  return apiRequest<EquipmentTypeSummary[]>(`/api/rooms/${roomId}/available-equipment`)
}

export function listRoomReservations(
  roomId: string,
  from?: string,
  to?: string,
): Promise<Reservation[]> {
  const query = new URLSearchParams()
  if (from) query.set('from', from)
  if (to) query.set('to', to)
  const qs = query.toString()
  return apiRequest<Reservation[]>(`/api/rooms/${roomId}/reservations${qs ? `?${qs}` : ''}`)
}

export function createReservation(
  roomId: string,
  payload: ReservationCreatePayload,
): Promise<Reservation> {
  return apiRequest<Reservation>(`/api/rooms/${roomId}/reservations`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getReservation(reservationId: string): Promise<Reservation> {
  return apiRequest<Reservation>(`/api/reservations/${reservationId}`)
}

export function updateReservationMetadata(
  reservationId: string,
  payload: ReservationUpdatePayload,
): Promise<Reservation> {
  return apiRequest<Reservation>(`/api/reservations/${reservationId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function activateReservation(reservationId: string): Promise<Reservation> {
  return apiRequest<Reservation>(`/api/reservations/${reservationId}/activate`, {
    method: 'POST',
  })
}

export function completeReservation(reservationId: string): Promise<Reservation> {
  return apiRequest<Reservation>(`/api/reservations/${reservationId}/complete`, {
    method: 'POST',
  })
}

export function expireReservation(reservationId: string): Promise<Reservation> {
  return apiRequest<Reservation>(`/api/reservations/${reservationId}/expire`, {
    method: 'POST',
  })
}

export function cancelReservation(reservationId: string): Promise<Reservation> {
  return apiRequest<Reservation>(`/api/reservations/${reservationId}/cancel`, {
    method: 'POST',
  })
}
