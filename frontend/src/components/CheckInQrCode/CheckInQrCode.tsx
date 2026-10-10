import { useEffect, useState } from 'react'
import QRCode from 'qrcode'
import './CheckInQrCode.css'

/** A scannable QR code for a room's check-in link (feature 014), rendered as inline SVG. */
export function CheckInQrCode({ link, roomName }: { link: string; roomName: string }) {
  const [svg, setSvg] = useState<{ link: string; markup: string } | null>(null)

  useEffect(() => {
    let ignore = false
    QRCode.toString(link, { type: 'svg', margin: 1 }).then(
      (markup) => { if (!ignore) setSvg({ link, markup }) },
      () => { if (!ignore) setSvg(null) },
    )
    return () => { ignore = true }
  }, [link])

  if (!svg || svg.link !== link) return null
  return (
    <div
      className="check-in-qr-code"
      role="img"
      aria-label={`QR-Code für den Check-in in ${roomName}`}
      // The markup is generated locally by the qrcode library from our own link, not from user input.
      dangerouslySetInnerHTML={{ __html: svg.markup }}
    />
  )
}
