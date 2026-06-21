export interface ExcelImportError {
  rowNumber: number
  fieldName: string
  reason: string
}

export interface ExcelImportResult {
  totalCount: number
  successCount: number
  failCount: number
  errors: ExcelImportError[]
}

export interface ImportExportLogRecord {
  id: number
  taskNo: string
  taskType: 'IMPORT' | 'EXPORT' | string
  templateCode: string
  fileId?: number
  totalCount: number
  successCount: number
  failCount: number
  status: string
  errorSummary?: string
  operatorId?: number
  operatorName?: string
  createdAt: string
}
