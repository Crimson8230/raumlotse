import type { ReactNode } from 'react'
import '../ui.css'
import './DataTable.css'

export interface DataTableColumn<T> {
  key: string
  header: string
  cell: (row: T) => ReactNode
  /** Zahlen und Zeiten in der Festbreitenschrift. */
  mono?: boolean
}

interface DataTableProps<T> {
  /** Pflicht: Beschriftung der Tabelle für Hilfstechnik. */
  caption: string
  columns: DataTableColumn<T>[]
  rows: T[]
  rowKey: (row: T) => string
  /** Schlüssel der Spalte, deren Zellen Zeilenüberschriften sind. */
  rowHeader?: string
  emptyText: string
}

/** Tabelle mit Caption, Spaltenüberschriften (scope) und optionaler Zeilenüberschrift; scrollt auf schmalen Schirmen im eigenen Bereich. */
export function DataTable<T>({ caption, columns, rows, rowKey, rowHeader, emptyText }: DataTableProps<T>) {
  return (
    <div className="ui-table-wrap" role="region" aria-label={caption} tabIndex={0}>
      <table className="ui-table">
        <caption className="ui-table__caption">{caption}</caption>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key} scope="col" className="ui-table__head">
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 && (
            <tr>
              <td colSpan={columns.length} className="ui-empty">
                {emptyText}
              </td>
            </tr>
          )}
          {rows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((column) => {
                const className = `ui-table__cell${column.mono ? ' ui-table__cell--mono' : ''}`
                return column.key === rowHeader ? (
                  <th key={column.key} scope="row" className={className}>
                    {column.cell(row)}
                  </th>
                ) : (
                  <td key={column.key} className={className}>
                    {column.cell(row)}
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
