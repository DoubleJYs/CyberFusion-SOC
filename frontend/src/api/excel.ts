import request from './request'
import type { ApiResult, PageQuery, PageResult } from '@/types/api'
import type { ExcelImportResult, ImportExportLogRecord } from '@/types/excel'

export interface ImportExportLogQuery extends PageQuery {
  templateCode?: string
  status?: string
}

export async function importExcel(templateCode: string, file: File): Promise<ExcelImportResult> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await request.post<ApiResult<ExcelImportResult>>(`/system/excel/imports/${templateCode}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return response.data.data
}

export async function fetchImportExportLogs(params: ImportExportLogQuery): Promise<PageResult<ImportExportLogRecord>> {
  const response = await request.get<ApiResult<PageResult<ImportExportLogRecord>>>('/system/excel/logs', { params })
  return response.data.data
}

export async function fetchImportExportLog(id: number): Promise<ImportExportLogRecord> {
  const response = await request.get<ApiResult<ImportExportLogRecord>>(`/system/excel/logs/${id}`)
  return response.data.data
}

export async function downloadExcelTemplate(templateCode: string) {
  const response = await request.get(`/system/excel/templates/${templateCode}`, { responseType: 'blob' })
  downloadBlob(response.data, `${templateCode}.xlsx`)
}

export async function exportImportExportLogs(params: ImportExportLogQuery) {
  const response = await request.get('/system/excel/logs/export', { params, responseType: 'blob' })
  downloadBlob(response.data, 'import-export-logs.xlsx')
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
