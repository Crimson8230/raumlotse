import type { ReactNode } from 'react'
import '../ui.css'
import './DataList.css'

interface DataListProps<T> {
  items: T[]
  itemKey: (item: T) => string
  renderItem: (item: T) => ReactNode
  /** Text, wenn keine Einträge vorhanden sind (statt eines leeren Kastens). */
  emptyText: string
  'aria-label'?: string
}

/** Echte Liste (`ul`/`li`) mit Worttext für den leeren Zustand. */
export function DataList<T>({ items, itemKey, renderItem, emptyText, 'aria-label': ariaLabel }: DataListProps<T>) {
  if (items.length === 0) return <p className="ui-empty">{emptyText}</p>
  return (
    <ul className="ui-data-list" aria-label={ariaLabel}>
      {items.map((item) => (
        <li key={itemKey(item)} className="ui-data-list__item">
          {renderItem(item)}
        </li>
      ))}
    </ul>
  )
}
